# cljfx job widget

```clojure
;; bring up the gui
(start)

;; add 20 dummy jobs to the queue that tick down the seconds until they're done
(run! joblib/create-job-add-to-queue! (gen-fns 20))

;; run *all* the dummy jobs
(joblib/start-jobs-in-queue!)

;; or, run just the first few jobs
(joblib/start-jobs-in-queue! 3)

;; or, create a monitor that watches the queue and ensures there are always N jobs running
(joblib/monitor! 3)

;; to stop the monitor from consuming jobs, keep a reference to it:
(def kill-monitor (joblib/monitor! 3))

;; and then call it when you're done:
(kill-monitor)

;; fetch results from the queue with:
(joblib/all-job-results)

;; or, fetch and remove the job from the queue with:
(joblib/pop-all-jobs!)

;; (this will return *all* of the job information, not just the results)

```
