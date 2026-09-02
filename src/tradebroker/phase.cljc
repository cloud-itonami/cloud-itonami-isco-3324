(ns tradebroker.phase
  "Phase 0->3 staged rollout for the ISCO-08 3324 independent trade
  brokerage actor -- the same rollout seam `cloud-itonami-isic-6612`'s
  `brokerage.phase` establishes, expressed for this repo's three ops.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-quote   -- quoting allowed, every write needs
                                 human approval.
    Phase 2  assisted-broker  -- adds shipment filing, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:quote-deal` may auto-commit.
                                 `:confirm-deal`/`:file-shipment` NEVER
                                 auto-commit, at any phase.

  `:confirm-deal` and `:file-shipment` are deliberately ABSENT from
  every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Confirming a
  deal binds a principal to a counterparty and filing a shipment moves
  goods; both are real-world acts against third parties, so both are
  always a licensed broker's call. `tradebroker.governor`'s
  `always-escalate-ops` enforces the same invariant independently --
  two layers, not one, agree on this. `:quote-deal` binds nobody (a
  quote is an indication, not an acceptance) and stays HARD-gated by
  the governor's ceiling and counterparty-verification rules, so it is
  the one auto-eligible op at phase 3.

  This namespace is the phase table plus the gate that reads it. It is
  pure: it never touches the store and never widens a governor
  disposition -- a phase can only add caution."
  )

(def read-ops
  "Ops that read without writing. This actor has none: every op it
  serves commits an operating record, so `read-ops` is empty rather
  than absent (the same posture `brokerage.phase` takes)."
  #{})

(def write-ops
  "Every op that can write, phase-gated below and governor-gated in
  `tradebroker.governor` independently."
  #{:quote-deal :confirm-deal :file-shipment})

;; NOTE the invariant: `:confirm-deal` and `:file-shipment` are members
;; of `write-ops` (governor-gated like any write) but are NEVER members
;; of any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                              :auto #{}}
   1 {:label "assisted-quote"  :writes #{:quote-deal}                   :auto #{}}
   2 {:label "assisted-broker" :writes #{:quote-deal :file-shipment}    :auto #{}}
   3 {:label "supervised-auto" :writes write-ops                        :auto #{:quote-deal}}})

(def default-phase 3)

(defn verdict->disposition
  "Map a TradeBrokerageGovernor verdict to a base disposition, before
  the phase gate sees it."
  [verdict]
  (cond (:hard? verdict)     :hold
        (:escalate? verdict) :escalate
        :else                :commit))

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition :commit|:escalate|:hold, :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins; the phase can
    never turn a hold into a commit).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even when the governor was clean.
  - an unknown op is in no phase's `:writes`, so it fails closed to
    HOLD by the same rule that holds a not-yet-enabled op.

  `op` is read from the request; the caller decides which request that
  is, so this stays a pure function of (phase, op, governor verdict)."
  [phase {:keys [op]} governor-disposition]
  (let [p     (if (contains? phases phase) phase default-phase)
        {:keys [writes auto]} (get phases p)
        read? (contains? read-ops op)]
    (cond
      ;; compliance wins -- a phase never widens a governor hold.
      (= :hold governor-disposition)
      {:disposition :hold :reason nil}

      ;; reads are never phase-restricted; they write nothing.
      read?
      {:disposition governor-disposition :reason nil}

      (not (contains? writes op))
      {:disposition :hold :reason :phase-disabled}

      (= :escalate governor-disposition)
      {:disposition :escalate :reason nil}

      (contains? auto op)
      {:disposition :commit :reason nil}

      :else
      {:disposition :escalate :reason :phase-approval})))
