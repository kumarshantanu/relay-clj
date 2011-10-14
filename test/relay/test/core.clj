(ns relay.test.core
  (:import (relay DaemonState$DaemonStateEnum))
  (:use [relay.core])
  (:use [clojure.test]))

(defn pause "Pause for some time" ([] (sleep 700)) ([n] (sleep n)))
(defn long-pause "Pause for a little longer" [] (sleep 2000))


(deftest test-make-daemon
  (let [d (make-daemon "test-daemon"
                       (fn [x] (println "Hello world in daemon" x)))
        r DaemonState$DaemonStateEnum/READY
        READY "Daemon should be in READY state"]
    (testing
      "=== Unstarted daemon ==="
      (is (not (nil? d)) "Create simple daemon")
      (is (= r (.getState d)) READY)
      (suspend! d)
      (is (= r (.getState d)) READY)
      (resume! d)
      (is (= r (.getState d)) READY)
      (stop! d)
      (is (= r (.getState d)) READY))
    (testing
      "=== Running daemon, suspending/resuming/stopping it ==="
      (let [done? (start! d)]
        (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d)))
        (is (not @done?))
        (suspend! d)
        (pause)
        (is (= DaemonState$DaemonStateEnum/SUSPENDED (.getState d)))
        (is (not @done?))
        (resume! d)
        (pause)
        (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d)))
        (is (not @done?))
        (stop! d)
        (pause)
        (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d)))
        (is @done?)))
    (testing
      "=== Running daemon and force-stopping it ==="
      (let [done? (start! d)]
        (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d))
            "Daemon should be in RUNNING state")
        (is (not @done?) "Daemon should NOT be done")
        (force-stop! d)
        (is (= DaemonState$DaemonStateEnum/FORCE_STOPPED (.getState d))
            "Daemon should be in FORCE_STOPPED state right now")
        (long-pause)
        (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d))
            "Daemon should automatically be converted to STOPPED state")
        (is @done? "Daemon should be done")))))


(deftest test-make-daemon-parallel-jobs
  (let [npar 5
        pool (make-fixed-thread-pool npar)
        d (make-daemon "parallel-jobs-daemon"
                       (fn [x] (println "Hello world in daemon" x))
                       :thread-pool pool
                       :parallel-jobs npar)]
    (testing
      "=== Daemon with parallel jobs ==="
      (let [done? (start! d)]
        (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d))
            "Daemon should be in RUNNING state")
        (is (not @done?) "Daemon should NOT be done")
        (stop! d)
        (long-pause)
        (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d))
            "Daemon should automatically be converted to STOPPED state")
        (is @done? "Daemon should be done")))))


(deftest test-make-daemon-shared-queues
  (let [q1 (make-queue)
        q2 (make-queue 5)
        odds-input  (atom 1)
        output-1 (atom [])
        output-2 (atom [])
        d1 (make-daemon "daemon-1"
                        (fn [d n] (println d n) n)
                        :args-maker (fn [d] (seq [d (swap! odds-input
                                                           (partial + 2))]))
                        :collector  (fn [d r]
                                      ((collector-outbox q1) d r)
                                      ((collector-outbox q2 100) d r)))
        slow-fn (fn [d n] (println d n) (sleep 200) n)
        d2 (make-daemon "daemon-2-slow" slow-fn
                        :args-maker (wrap-args-maker-max-size
                                      (args-maker-inbox q1) q1 5)
                        :collector (fn [d r] (swap! output-1 #(conj % r))))
        d3 (make-daemon "daemon-3-slow" slow-fn
                        :args-maker (wrap-args-maker-max-size
                                      (args-maker-inbox q2) q2)
                        :collector (fn [d r] (swap! output-2 #(conj % r))))
        c1 (start! d1)
        c2 (start! d2)
        c3 (start! d3)]
    (testing
      "=== Daemons running a pipeline ==="
      (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d1))
          "Daemon-1 should be in RUNNING state")
      (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d2))
          "Daemon-2 should be in RUNNING state")
      (is (= DaemonState$DaemonStateEnum/RUNNING (.getState d3))
          "Daemon-3 should be in RUNNING state")
      (is (not @c1) "Daemon-1 should NOT be done yet")
      (is (not @c2) "Daemon-2 should NOT be done yet")
      (is (not @c3) "Daemon-3 should NOT be done yet")
      (pause 10000) ; give a pause long enough to get some work done
      (stop! d1)
      (stop! d2)
      (stop! d3)
      (long-pause)
      (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d1))
          "Daemon-1 should automatically be converted to STOPPED state")
      (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d2))
          "Daemon-2 should automatically be converted to STOPPED state")
      (is (= DaemonState$DaemonStateEnum/STOPPED (.getState d3))
          "Daemon-3 should automatically be converted to STOPPED state")
      (is @c1 "Daemon-1 should be done")
      (is @c2 "Daemon-2 should be done")
      (is @c3 "Daemon-3 should be done")
      (is (not (empty? @output-1)) "Results-1 should not be empty")
      (is (every? odd? @output-1) "All results-1 should be odd")
      (is (not (empty? @output-2)) "Results-2 should not be empty")
      (is (every? odd? @output-2) "All results-2 should be odd"))))
