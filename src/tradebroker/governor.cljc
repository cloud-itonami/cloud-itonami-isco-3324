(ns tradebroker.governor
  "TradeBrokerageGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every deal confirmation and shipment filing an advisor may
  propose for a principal. The governor never dispatches deals itself
  and never confirms a deal above the principal's registered
  trade-authorization ceiling. Modeled on
  cloud-itonami-isco-3322's salesrep.governor.
  Task twist: a deal value is an arithmetic ceiling
  against the principal's registered trade-authorization ceiling, and a
  deal cannot be confirmed until both principal and counterparty have
  passed verification.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. principal provenance — the buyer/seller/trader
                              must be registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never confirms deals and
                             never confirms a deal above the
                             registered trade-authorization ceiling;
                             it only gates what the advisor may
                             publish).
    3. deal basis           — a deal proposal must cite a
                             REGISTERED deal belonging to this
                             principal.
    4. deal-wrong-principal — a deal must belong to the request's
                             principal.
    5. counterparty-unverified — both principal and counterparty must
                             have passed verification (confirming
                             without verification is incomplete
                             counterparty due diligence, not routine
                             deal service).
    6. trade-authority-exceeded — the deal value must not
                             exceed the principal's registered
                             trade-authorization ceiling (confirming
                             beyond the principal's registered ceiling
                             is unauthorized confirmation, not routine
                             deal service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    7. :op :confirm-deal (deal confirmation always requires human
                         sign-off per Trust Control 1).
    8. :op :file-shipment (shipment filing always requires human
                          sign-off per Trust Control 2).
    9. low confidence (< `confidence-floor`)."
  (:require [tradebroker.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:confirm-deal :file-shipment})
(def ^:private normal-ops #{:quote-deal})

(defn- hard-violations [{:keys [request proposal]} principal-record deal]
  (let [{:keys [op deal-value]} proposal]
    (cond-> []
            (nil? principal-record)
            (conj {:rule :no-principal :detail "未登録 principal"})

            (not= :propose (:effect proposal))
            (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録権限上限超過の確認を直接実行しない）"})

            (nil? deal)
            (conj {:rule :unknown-deal :detail "未登録 deal への提案は不可"})

            (and deal (not= (:principal-id deal) (:principal-id request)))
            (conj {:rule :deal-wrong-principal :detail "deal が別 principal のもの"})

            (and principal-record deal (not (:verified deal)))
            (conj {:rule :counterparty-unverified
                   :detail "未検証の相手方との取引確認は不完全な相手方デューデリジェンスであって通常の取引業務ではない"})

            (and principal-record deal (number? deal-value)
                 (> deal-value (:trade-authorization-ceiling principal-record)))
            (conj {:rule :trade-authority-exceeded
                   :detail (str "取引金額 " deal-value " > 登録済み上限 "
                                (:trade-authorization-ceiling principal-record)
                                "（登録上限を超える確認は無許可確認であって通常の取引業務ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `tradebroker.store/Store`. Pure — never
  mutates the store, never confirms a deal above the registered
  trade-authorization ceiling."
  [request context proposal store]
  (let [principal-record (store/principal store (:principal-id request))
        deal (some->> (:deal-id proposal) (store/deal store))
        hard (hard-violations {:request request :proposal proposal}
                              principal-record deal)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
