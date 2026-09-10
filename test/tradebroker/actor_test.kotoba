(ns tradebroker.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [tradebroker.actor :as actor]
            [tradebroker.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-principal! st {:principal-id "principal-1" :name "Importer A"
                                   :trade-authorization-ceiling 100000
                                   :verified true})
    (store/register-deal! st {:deal-id "D-1" :principal-id "principal-1"
                              :counterparty-id "exporter-1" :commodity "coffee"
                              :deal-value 50000 :verified true})
    st))

(deftest commits-a-within-ceiling-verified-quote
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:principal-id "principal-1" :op :quote-deal :stake :low
                 :deal-id "D-1" :deal-value 50000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "principal-1"))))))

(deftest holds-an-above-ceiling-deal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:principal-id "principal-1" :op :quote-deal :stake :low
                 :deal-id "D-1" :deal-value 150000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "principal-1")))))

(deftest interrupts-then-approves-confirm-deal-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:principal-id "principal-1" :op :confirm-deal :stake :low
                 :deal-id "D-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "principal-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "principal-1")))))))
