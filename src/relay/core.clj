(ns relay.core
  (:import (java.util            Collection Queue)
           (java.util.concurrent BlockingQueue ConcurrentLinkedQueue
                                 LinkedBlockingQueue
                                 ExecutorService Executors
                                 Future TimeUnit)
           (clojure.lang         IDeref)
           (relay                GenericDaemon TimerUtil)))


(defn ^ExecutorService cached-thread-pool
  "Returns a thread pool that creates new threads as needed, but will reuse
  previously constructed threads when they are available."
  []
  (Executors/newCachedThreadPool))


(defn ^ExecutorService fixed-thread-pool
  "Returns a thread pool that reuses a fixed number of threads operating off a
  shared unbounded queue."
  [n]
  (Executors/newFixedThreadPool n))


(defn ^ExecutorService single-thread-pool
  "Returns a thread pool that creates an Executor that uses a single worker
  thread operating off an unbounded queue."
  []
  (Executors/newSingleThreadExecutor))


(defn ^GenericDaemon make-daemon
  "Return an unstarted daemon that when started, will asynchronously execute the
  function `f` (which takes daemon object as argument) endlessly in a loop until
  either change of state is requested e.g. SUSPEND, RESUME, STOP, or `f` throws
  an exception.
  Optional arguments:
    idle-millis   (Long)     no. of milliseconds to sleep in the loop when idle
    thread-pool   (ExecutorService) a thread pool used to execute jobs
    parallel-jobs (Integer)  no. of jobs to run in parallel using `thread-pool`
    args-maker    (1-arg function) returns a sequence of args to be applied to `f`
    collector     (2-arg function) for post-processing action with result
  Notes:
    1. `args-maker` and `collector` are called on a FIFO basis for each call of
       `f` - if a function completes earlier than the one before it, it waits in
       an internal queue for its turn."
  [daemon-name f & {:keys [idle-millis ^ExecutorService thread-pool
                           ^int parallel-jobs args-maker collector]
                    :or {idle-millis    2000
                         thread-pool    (single-thread-pool)
                         parallel-jobs  1
                         args-maker     (fn [daemon] (seq [daemon]))
                         collector      (fn [daemon result] nil)}
                    :as opt}]
  (GenericDaemon. daemon-name f idle-millis thread-pool parallel-jobs
                  args-maker collector))


(defn start!
  "Start an daemon using an executor service. The return value can be deref'ed
  (as boolean) to find out whether the daemon is terminated."
  [^GenericDaemon daemon ^ExecutorService pool]
  (let [f (.submit pool daemon)]
    (reify IDeref
      (deref [this] (.isDone ^Future f)))))


(defn suspend!
  "Suspend the daemon, as in do not start new jobs."
  [^GenericDaemon daemon]
  (.suspend daemon))


(defn resume!
  "Resume a suspended daemon."
  [^GenericDaemon daemon]
  (.resume daemon))


(defn stop!
  "Stop the daemon after all current jobs are done and their results are sent
  to the daemon `collector`."
  [^GenericDaemon daemon]
  (.stop daemon))


(defn force-stop!
  "Stop the daemon immediately. Does not ensure that all job results are sent
  to the daemon `collector`."
  [^GenericDaemon daemon]
  (.forceStop daemon))


(defn now
  "Return current time"
  []
  (System/currentTimeMillis))


(defn sleep
  "Sleep for `millis` milliseconds"
  [millis]
  (TimerUtil/sleep millis))


(defn args-maker-inbox
  "Create an args-maker (for make-daemon) that reads from `inbox` queue. The
  `timeout-millis` argument can be applied to java.util.concurrent.BlockingQueue
  instances only."
  ([^Queue inbox]
    (fn [^GenericDaemon daemon]
      (when-let [v (.poll inbox)]
        (seq [daemon v]))))
  ([^BlockingQueue inbox timeout-millis]
    (fn [^GenericDaemon daemon]
      (when-let [v (.poll inbox timeout-millis ^TimeUnit TimeUnit/MILLISECONDS)]
        (seq [daemon v])))))


(defn wrap-args-maker-max-size
  "Wrap args maker function `f` to honor the `max-size` constraint for a
  collection `coll`. Typical use of this may be to check whether an outbox queue
  is below a certain size limit."
  ([f ^Collection coll max-size]
    (fn [^GenericDaemon daemon]
      (when (< (.size coll) max-size)
        (f daemon))))
  ([f ^BlockingQueue q]
    (fn [^GenericDaemon daemon]
      (when (pos? (.remainingCapacity q))
        (f daemon)))))


(defn collector-outbox
  "Create a collector (for make-daemon) that writes results to `outbox` queue.
  `timeout-millis` argument can be applied to java.util.concurrent.BlockingQueue
  instances only."
  ([^Queue outbox]
    (fn [^GenericDaemon daemon result]
      (.add outbox result)))
  ([^BlockingQueue outbox timeout-millis]
    (fn [^GenericDaemon daemon result]
      (or (.offer outbox result timeout-millis ^TimeUnit TimeUnit/MILLISECONDS)
          (throw (IllegalStateException.
                   (format "Failed to add result '%s' to queue '%s' in %d ms"
                           (if (nil? result) "<nil>" (str result))
                           (.toString outbox) timeout-millis)))))))


(defn ^LinkedBlockingQueue make-queue
  "Create and return a queue that can be shared between producer and consumer.
  When `capacity` (integer) is specified, a bounded queue is created."
  ([]
    (LinkedBlockingQueue.))
  ([^Integer capacity]
    (LinkedBlockingQueue. capacity)))


(defn ^ConcurrentLinkedQueue make-queue-notimeout
  []
  (ConcurrentLinkedQueue.))
