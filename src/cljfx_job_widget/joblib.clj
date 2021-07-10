(ns cljfx-job-widget.joblib
  (:require
   [flatland.ordered.map :refer [ordered-map]])
  (:import
   [java.util UUID]))

(def ^:dynamic tick (constantly nil))
(def ^:dynamic -queue (atom (ordered-map)))

(defn make-ticker
  "returns a fn that accepts a float value between 0.0 and 1.0 inclusive.
  called with a float will update the job's progress in the queue.
  called without arguments returns the job's current progress."
  [queue-atm job-id]
  (fn [& [pct]]
    (when pct
      (swap! queue-atm assoc-in [job-id :progress] pct))
    (:progress (get @queue-atm job-id))))

(defn job
  "given a function `f`, returns a 'job' (fn) that accepts an atom to be used as a progress meter.
  'starting' this job returns a future that can be deref'ed to retrieve the result or cancelled with `future-cancel`."
  [f]
  (fn [tick-fn]
    (future 
      (binding [tick tick-fn]
        (try
          (f)
          (catch Exception uncaught-exc
            uncaught-exc))))))

(defn job-info
  "given a job `j` and optional map with keys :job-id, return a struct that can be added to a queue"
  [j & [{:keys [job-id]}]]
  {:job j
   :job-id (or job-id (java.util.UUID/randomUUID))
   :progress nil})

(defn add-to-queue!
  ([ji]
   (add-to-queue! -queue ji))
  ([queue-atm ji]
   (let [job-id (:job-id ji)]
     (dosync
      (when-not (contains? @queue-atm job-id)
        (swap! queue-atm assoc job-id ji)
        job-id)))))

(defn create-job-add-to-queue!
  "convenience, takes a function `f`, wraps it in a `job`, gives it a tick function and adds it to the queue.
  returns the job ID"
  ([f]
   (create-job-add-to-queue! -queue f))
  ([queue-atm f]
   (add-to-queue! queue-atm (job-info (job f)))))

(defn get-job
  "fetches the job from the queue"
  ([job-id]
   (get-job @-queue job-id))
  ([queue job-id]
   (:job (get queue job-id))))

(defn -job-running?
  "returns `true` if job was found and isn't finished or cancelled yet, else `false`."
  [job]
  (and (future? job)
       (not (future-done? job))))

(defn job-running?
  "returns `true` if job was found and isn't finished or cancelled yet, else `false`."
  ([job-id]
   (job-running? @-queue job-id))
  ([queue job-id]
   (-job-running? (get-job queue job-id))))

(defn job-cancelled?
  "returns `true` if job was found was has been cancelled, else `false`."
  ([job-id]
   (job-cancelled? @-queue job-id))
  ([queue job-id]
   (let [job (get-job queue job-id)]
     (and (future? job)
          (future-cancelled? job)))))

(defn -job-done?
  "returns `true` if job was found and has completed, this includes being cancelled or failing with an exception, otherwise `false`"
  [job]
  (and (future? job)
       (future-done? job)))

(defn job-done?
  "returns `true` if job was found and has completed, this includes being cancelled or failing with an exception, otherwise `false`"
  ([job-id]
   (job-done? @-queue job-id))
  ([queue job-id]
   (-job-done? (get-job queue job-id))))

(defn start-job!
  "starts a job, updates the job-info with the new `future` and returns it."
  ([job-id]
   (start-job! -queue job-id))
  ([queue-atm job-id]
   (dosync
    (when-not (job-done? @queue-atm job-id)
      (when-let [ji (get @queue-atm job-id)]
        (let [j-fn (:job ji)
              ;; `tick` updates the job's progress in the queue
              tick-fn (make-ticker queue-atm job-id)
              running-job (j-fn tick-fn)]
          ;; replace the job fn with the future object
          (swap! queue-atm assoc-in [job-id :job] running-job)
          running-job))))))

(defn cancel-job!
  "returns `true` if job was found and successfully cancelled. cancelled and completed jobs cannot be cancelled."
  ([job-id]
   (cancel-job! @-queue job-id))
  ([queue job-id]
   (let [job (get-job queue job-id)]
     (and (future? job)
          (future-cancel job)))))

(defn job-results
  "returns the results of the job with the given `job-id`.
  if an exception was thrown during the job, the exception is returned.
  if the job was cancelled, the cancellation exception is returned."
  ([job-id]
   (job-results @-queue job-id))
  ([queue job-id]
   (try
     (let [future-obj (get-job queue job-id)]
       @future-obj)
     (catch java.util.concurrent.CancellationException ce
       ;; deref'ing a cancelled job raises a cancellation exception.
       ce))))

(defn all-job-results
  ([]
   (all-job-results @-queue))
  ([queue]
   (->> queue keys (mapv job-results))))

(defn pop-job!
  "removes the job info from the queue and returns the derefable future, regardless of whether the job has been started, cancelled, completed etc"
  ([job-id]
   (pop-job! -queue job-id))
  ([queue-atm job-id]
   (dosync
    (when-let [ji (get @queue-atm job-id)]
      (swap! queue-atm dissoc job-id)
      ji))))

(defn pop-all-jobs!
  ([]
   (pop-all-jobs! -queue))
  ([queue-atm]
   (dosync
    (->> @queue-atm keys (mapv (partial pop-job! queue-atm))))))

(defn start-jobs-in-queue!
  "given a queue of jobs in various states and N number of jobs to be running, ensures that many jobs are running"
  ([]
   (start-jobs-in-queue! -queue nil))
  ([n-jobs-running]
   (start-jobs-in-queue! -queue n-jobs-running))
  ([queue-atm n-jobs-running]
   (dosync
    (let [job-done?* (fn [[_ ji]]
                       (-job-done? (:job ji)))

          ;; ignore jobs that are done
          queue (remove job-done?* @queue-atm)

          job-running?* (fn [[_ ji]]
                          (-job-running? (:job ji)))
          
          ;; leaving just those running or not started
          [jobs-running, jobs-not-started] (split-with job-running?* queue)
          
          n-jobs-running (or n-jobs-running (count jobs-not-started))
          num-to-run (- n-jobs-running (count jobs-running))]
      (when (> num-to-run 0)
        (->> jobs-not-started
             (take num-to-run)
             keys
             (run! start-job!)))))))
