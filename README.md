# cljfx job widget

```clojure
(start) ;; bring up the gui
(run! joblib/create-job-add-to-queue! (gen-fns 20)) ;; add 20 dummy jobs that slowly complete
(joblib/start-jobs-in-queue!) ;; run the dummy jobs
```
