(ns tradebroker.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [tradebroker.store :as store]
            [tradebroker.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-principal! st {:principal-id "principal-1" :name "Importer A"
                                   :trade-authorization-ceiling 100000
                                   :verified true})
    (store/register-deal! st {:deal-id "D-1" :principal-id "principal-1"
                              :counterparty-id "exporter-1" :commodity "coffee"
                              :deal-value 50000 :verified true})
    st))

(defn- deal-op [op confidence deal-value]
  {:op op :effect :propose :deal-id "D-1"
   :deal-value deal-value
   :confidence confidence :stake :low})

(def ^:private req {:principal-id "principal-1"})

(deftest ok-within-ceiling-and-verified
  (let [st (fresh-store)
        v (governor/check req {} (deal-op :quote-deal 0.9 50000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceiling-boundary
  (testing "the trade-authorization-ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (deal-op :quote-deal 0.9 100000) st)]
      (is (:ok? v)))))

(deftest hard-on-deal-exceeds-ceiling
  (testing "confirming a deal above the principal's registered trade-authorization ceiling is unauthorized confirmation, not routine deal"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (deal-op :quote-deal 0.99 150000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :trade-authority-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-counterparty-unverified
  (testing "confirming a deal without counterparty verification is incomplete counterparty due diligence, not proper deal service"
    (let [st (fresh-store)
          _ (store/register-deal! st {:deal-id "D-unverified" :principal-id "principal-1"
                                      :counterparty-id "exporter-2" :commodity "cocoa"
                                      :deal-value 25000 :verified false})
          v (governor/check req {} (assoc (deal-op :quote-deal 0.99 25000) :deal-id "D-unverified" :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :counterparty-unverified (:rule %)) (:violations v))))))

(deftest hard-on-unknown-deal
  (let [st (fresh-store)
        v (governor/check req {} (assoc (deal-op :quote-deal 0.9 50000) :deal-id "D-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-deal (:rule %)) (:violations v)))))

(deftest hard-on-foreign-deal
  (let [st (fresh-store)]
    (store/register-principal! st {:principal-id "principal-2" :name "Importer B"
                                   :trade-authorization-ceiling 75000
                                   :verified true})
    (store/register-deal! st {:deal-id "D-2" :principal-id "principal-2"
                              :counterparty-id "exporter-2" :commodity "tea"
                              :deal-value 40000 :verified true})
    (let [v (governor/check {:principal-id "principal-2"} {} (assoc (deal-op :quote-deal 0.9 50000) :deal-id "D-1") st)]
      (is (:hard? v))
      (is (some #(= :deal-wrong-principal (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-principal
  (let [st (fresh-store)
        v (governor/check {:principal-id "nobody"} {} (deal-op :quote-deal 0.9 50000) st)]
    (is (:hard? v))
    (is (some #(= :no-principal (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (deal-op :quote-deal 0.9 50000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-confirm-deal-even-at-high-confidence
  (testing "deal confirmation always requires human sign-off per Trust Control 1"
    (let [st (fresh-store)
          v (governor/check req {} {:op :confirm-deal :effect :propose
                                    :deal-id "D-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-file-shipment-even-at-high-confidence
  (testing "shipment filing always requires human sign-off per Trust Control 2"
    (let [st (fresh-store)
          v (governor/check req {} {:op :file-shipment :effect :propose
                                    :deal-id "D-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (deal-op :file-shipment 0.3 50000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
