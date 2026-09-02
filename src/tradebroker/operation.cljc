(ns tradebroker.operation
  "OperationActor -- one independent-trade-brokerage operation = one
  supervised actor run, expressed as a langgraph-clj StateGraph. This is
  the OS-facing entry point for this vertical (the shape
  `scripts/itonami-os-maturity-tick.cljs` requires: `phase.cljc` op
  sets + `operation/build` + a `langgraph.graph` actor + `store/seed-db`
  + a governor).

  It does NOT restate the graph. `tradebroker.actor/build-graph` owns
  the topology; `build` here injects `tradebroker.phase/gate` into its
  `:decide` seam. One graph, one place to change it -- restating the
  nodes here is how the two copies drift apart, which is the defect
  `cloud-itonami/ma`'s ADR 0001 recorded for this business.

    :intake -> :advise -> :govern -> :decide -+-> :commit
                                              +-> :request-approval (interrupt)
                                              +-> :hold

  The advisor (Trade Advisor) is sealed into `:advise` and only ever
  proposes; the TradeBrokerageGovernor censors independently at
  `:govern`; the rollout phase can then only add caution at `:decide`.
  No unbounded inner loop -- each operation is auditable and
  checkpointed, so an interrupted run resumes after human sign-off."
  (:require [langgraph.graph :as g]
            [tradebroker.actor :as actor]
            [tradebroker.phase :as phase]))

(defn build
  "Compile an OperationActor bound to `store` (any
  `tradebroker.store/Store`).

  opts:
    :advisor      -- a `tradebroker.advisor/Advisor` (default: mock)
    :checkpointer -- langgraph checkpointer (default: in-mem)
    :phase        -- rollout phase 0-3 (default: the request context's
                     `:phase`, else `tradebroker.phase/default-phase`)

  Absent keys are dropped rather than passed as nil, so
  `actor/build-graph`'s own `:or` defaults still apply."
  [store & [{:keys [advisor checkpointer phase]}]]
  (actor/build-graph
   (cond-> {:store store
            :phase-gate (fn [request context _verdict base]
                          (phase/gate (or phase
                                          (:phase context)
                                          phase/default-phase)
                                      request
                                      base))}
     advisor      (assoc :advisor advisor)
     checkpointer (assoc :checkpointer checkpointer))))

(defn run-operation!
  "Run one operation to completion or to the approval interrupt.
  `thread-id` scopes checkpointing so the run can resume."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: resuming the thread IS the approval, so the
  interrupted `:request-approval` node advances to `:commit`."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
