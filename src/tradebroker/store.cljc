(ns tradebroker.store
  "SSoT for the ISCO-08 3324 independent trade brokerage practice
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section; README's 'Robotics premise' — a document-handling and
  shipment-tracking robot manages trade deals under this advisor/governor
  pair, which never confirms a deal above the principal's registered
  trade-authorization ceiling and never brokers a deal without verifying
  both parties). Modeled on cloud-itonami-isco-3322's salesrep.store.

  Domain:

    principal — a registered party (buyer/seller/trader)
                (:principal-id, :name, :trade-authorization-ceiling,
                 :verified)
    deal      — a registered trade deal between principals
                {:deal-id :principal-id :counterparty-id :commodity
                 :deal-value}. `:verified` is whether the principal and
                counterparty have both passed verification — broking a deal
                without verification is incomplete counterparty due diligence.
    record    — a committed operating record (a confirmed deal or filed
                shipment) — written ONLY via commit-record!.
    ledger    — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (principal [s principal-id])
  (deal [s deal-id])
  (records-of [s principal-id])
  (ledger [s])
  (register-principal! [s principal])
  (register-deal! [s d])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (principal [_ principal-id] (get-in @a [:principals principal-id]))
  (deal [_ deal-id] (get-in @a [:deals deal-id]))
  (records-of [_ principal-id] (filter #(= principal-id (:principal-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-principal! [s principal]
    (swap! a assoc-in [:principals (:principal-id principal)] principal) s)
  (register-deal! [s d]
    (swap! a assoc-in [:deals (:deal-id d)] d) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:principals {} :deals {} :records [] :ledger []}
                                   seed)))))

;; ----------------------------- deterministic demo seed -----------------------------

(defn- demo-data
  "The demo principal/deal set. Deterministic -- no clock, no rand, no
  I/O -- so every caller (tests, the OS console, a sim run) sees the
  same store and a diff in behaviour is a diff in the actor.

  `principal-2` is registered but UNVERIFIED, and `D-3` is a registered
  deal that has not cleared counterparty verification: both exist so
  the governor's `:counterparty-unverified` and
  `:trade-authority-exceeded` HARD rules have something real to refuse.
  A seed that only contains the happy path cannot show a gate working."
  []
  {:principals
   {"principal-1" {:principal-id "principal-1" :name "Importer A"
                   :trade-authorization-ceiling 100000 :verified true}
    "principal-2" {:principal-id "principal-2" :name "Trader B"
                   :trade-authorization-ceiling 20000 :verified false}}
   :deals
   {"D-1" {:deal-id "D-1" :principal-id "principal-1"
           :counterparty-id "exporter-1" :commodity "coffee"
           :deal-value 50000 :verified true}
    "D-2" {:deal-id "D-2" :principal-id "principal-1"
           :counterparty-id "exporter-2" :commodity "cocoa"
           :deal-value 150000 :verified true}
    "D-3" {:deal-id "D-3" :principal-id "principal-2"
           :counterparty-id "exporter-3" :commodity "sugar"
           :deal-value 5000 :verified false}}})

(defn seed-db
  "A MemStore seeded with the demo principal/deal set. The
  deterministic default."
  []
  (mem-store (demo-data)))
