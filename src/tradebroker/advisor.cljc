(ns tradebroker.advisor
  "Trade Advisor — the advisor named in this repository's README,
  proposing a deal confirmation or shipment filing from a principal,
  deal details and commodity terms.
  Swappable mock/llm; the advisor ONLY proposes — `tradebroker.governor`
  checks the trade authority ceiling and counterparty verification
  independently and always escalates confirm-deal and file-shipment
  decisions. Modeled on cloud-itonami-isco-3322's salesrep.advisor.

  A proposal: {:op :quote-deal|:confirm-deal|:file-shipment
               :effect :propose :deal-id str :deal-value number
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake deal-id deal-value] :as request}]
  {:op op
   :effect :propose
   :deal-id deal-id
   :deal-value (or deal-value 0)
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for principal " (:principal-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a trade broker advisor. Given a request, propose an
   :op, the :deal-id, :deal-value, an honest :confidence and a :stake.
   Never propose a deal above the principal's registered trade-authorization
   ceiling — the governor checks it against the registered principal record.
   Confirm-deal and file-shipment always require human sign-off
   regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "deal request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
