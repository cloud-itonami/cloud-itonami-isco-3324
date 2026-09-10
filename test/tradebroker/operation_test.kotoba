(ns tradebroker.operation-test
  "End-to-end through the real compiled graph and a real store, not
  through a hand-built verdict. The phase gate is only interesting if
  it changes what reaches the SSoT, so every case below asserts the
  store contents as well as the disposition."
  (:require [clojure.test :refer [deftest is testing]]
            [tradebroker.operation :as operation]
            [tradebroker.phase :as phase]
            [tradebroker.store :as store]))

(deftest seed-db-is-deterministic-and-carries-refusable-cases
  (let [a (store/seed-db)
        b (store/seed-db)]
    (is (= (store/principal a "principal-1") (store/principal b "principal-1")))
    (is (some? (store/deal a "D-1")))
    (testing "the seed can exercise the governor's HARD rules, not just the happy path"
      (is (false? (:verified (store/deal a "D-3")))
          "an unverified counterparty must exist to refuse")
      (is (> (:deal-value (store/deal a "D-2"))
             (:trade-authorization-ceiling (store/principal a "principal-1")))
          "an over-ceiling deal must exist to refuse"))
    (is (empty? (store/ledger a)) "a fresh seed has committed nothing")))

(deftest phase-3-commits-a-governor-clean-quote
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        result (operation/run-operation!
                graph
                {:principal-id "principal-1" :op :quote-deal :stake :low
                 :deal-id "D-1" :deal-value 50000}
                {} "op-commit")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "principal-1"))))))

(deftest phase-0-holds-the-very-same-quote
  (testing "identical request and store -- only the phase differs, so a
            hold here is the phase gate and nothing else"
    (let [st (store/seed-db)
          graph (operation/build st {:phase 0})
          result (operation/run-operation!
                  graph
                  {:principal-id "principal-1" :op :quote-deal :stake :low
                   :deal-id "D-1" :deal-value 50000}
                  {} "op-hold")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "principal-1"))
          "nothing may reach the SSoT at phase 0")
      (is (= :phase-disabled
             (->> (get-in result [:state :audit])
                  (keep :phase-reason)
                  first))
          "and it must be held for the reason the gate names"))))

(deftest confirm-deal-escalates-at-phase-3-and-commits-only-after-approval
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        interrupted (operation/run-operation!
                     graph
                     {:principal-id "principal-1" :op :confirm-deal :stake :low
                      :deal-id "D-1"}
                     {} "op-approve")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "principal-1"))
        "a deal confirmation must not commit before a human resumes it")
    (let [resumed (operation/approve! graph "op-approve")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "principal-1")))))))

(deftest an-over-ceiling-deal-is-held-even-at-the-most-permissive-phase
  (testing "the governor's HARD rule survives the phase gate"
    (let [st (store/seed-db)
          graph (operation/build st {:phase 3})
          result (operation/run-operation!
                  graph
                  {:principal-id "principal-1" :op :quote-deal :stake :low
                   :deal-id "D-2" :deal-value 150000}
                  {} "op-ceiling")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "principal-1"))))))

(deftest an-unverified-counterparty-is-held-even-at-the-most-permissive-phase
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        result (operation/run-operation!
                graph
                {:principal-id "principal-2" :op :quote-deal :stake :low
                 :deal-id "D-3" :deal-value 5000}
                {} "op-unverified")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "principal-2")))))

(deftest the-phase-comes-from-the-request-context-when-build-is-not-pinned
  (testing "an OS caller that injects :phase per run gets that phase"
    (let [st (store/seed-db)
          graph (operation/build st)
          result (operation/run-operation!
                  graph
                  {:principal-id "principal-1" :op :quote-deal :stake :low
                   :deal-id "D-1" :deal-value 50000}
                  {:phase 0} "op-ctx")]
      (is (= :hold (:disposition (:state result)))
          "context :phase 0 must hold what default-phase would commit")))
  (is (= 3 phase/default-phase)))
