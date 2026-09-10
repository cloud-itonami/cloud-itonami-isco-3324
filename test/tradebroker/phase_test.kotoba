(ns tradebroker.phase-test
  "The phase gate has to be shown REFUSING, and refusing for the reason
  it names -- a test that only asserts the final disposition counts a
  run that failed for an unrelated cause as a working gate
  (CLAUDE.md, 'the six questions', Q6). Every refusal below pins the
  `:reason` literal, and every refusal is paired with the same op and
  the same governor verdict at a phase that ALLOWS it, so a gate that
  simply always held could not pass."
  (:require [clojure.test :refer [deftest is testing]]
            [tradebroker.phase :as phase]))

(deftest quote-deal-is-held-at-phase-0-and-committed-at-phase-3
  (testing "same op, same clean governor verdict -- only the phase differs"
    (is (= {:disposition :hold :reason :phase-disabled}
           (phase/gate 0 {:op :quote-deal} :commit))
        "phase 0 writes nothing, and says so by name")
    (is (= {:disposition :commit :reason nil}
           (phase/gate 3 {:op :quote-deal} :commit))
        "phase 3 auto-commits a governor-clean quote")))

(deftest confirm-deal-and-file-shipment-never-auto-commit-at-any-phase
  (testing "structural: absent from every phase's :auto set"
    (doseq [[p {:keys [auto]}] phase/phases
            op [:confirm-deal :file-shipment]]
      (is (not (contains? auto op))
          (str "phase " p " must not auto-commit " op))))
  (testing "and the gate enforces it even with a clean governor"
    (is (= {:disposition :escalate :reason :phase-approval}
           (phase/gate 3 {:op :confirm-deal} :commit))
        "confirming binds a principal to a counterparty -- always a human's call")
    (is (= {:disposition :escalate :reason :phase-approval}
           (phase/gate 3 {:op :file-shipment} :commit))
        "filing a shipment moves goods -- always a human's call")))

(deftest a-phase-never-widens-a-governor-hold
  (testing "compliance wins at every phase, including the most permissive"
    (doseq [p (keys phase/phases)
            op phase/write-ops]
      (is (= :hold (:disposition (phase/gate p {:op op} :hold)))
          (str "phase " p " must not turn a governor hold into a write for " op)))))

(deftest a-governor-escalation-is-never-downgraded-to-a-commit
  (doseq [p (keys phase/phases)]
    (let [{:keys [disposition]} (phase/gate p {:op :quote-deal} :escalate)]
      (is (contains? #{:escalate :hold} disposition)
          (str "phase " p " must not auto-commit what the governor escalated")))))

(deftest an-unknown-op-fails-closed
  (testing "an op in no phase's :writes is held, not silently committed"
    (is (= {:disposition :hold :reason :phase-disabled}
           (phase/gate 3 {:op :wire-funds-somewhere} :commit)))
    (is (= {:disposition :hold :reason :phase-disabled}
           (phase/gate 3 {:op nil} :commit)))))

(deftest an-out-of-range-phase-falls-back-to-the-default-phase
  (is (= (phase/gate phase/default-phase {:op :quote-deal} :commit)
         (phase/gate 99 {:op :quote-deal} :commit))
      "an unrecognized phase must not be more permissive than the default")
  (is (contains? phase/phases phase/default-phase)))

(deftest the-op-sets-agree-with-the-phase-table
  (testing "no phase may enable a write that is not a declared write-op"
    (doseq [[p {:keys [writes auto]}] phase/phases]
      (is (every? #(contains? phase/write-ops %) writes)
          (str "phase " p " enables an undeclared op"))
      (is (every? #(contains? writes %) auto)
          (str "phase " p " auto-commits an op it does not even enable"))))
  (testing "read and write op sets do not overlap"
    (is (empty? (filter #(contains? phase/write-ops %) phase/read-ops)))))
