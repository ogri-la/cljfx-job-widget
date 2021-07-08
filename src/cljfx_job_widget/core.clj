(ns cljfx-job-widget.core
  (:require
   [clojure.tools.namespace.repl :as tn :refer [refresh]]
   [flatland.ordered.map :refer [ordered-map]]
   [cljfx.api :as fx]
   [cljfx-job-widget.joblib :as joblib]
   )
  (:import
   [java.util UUID]))

(def lock-obj (Object.))

(defn syncout
  [& x]
  (locking lock-obj
    (apply println x)))

(defn print-thread-id-and-wait
  [duration-secs]
  (syncout (format "I am thread %s, waiting for %s seconds ..." (.getId (Thread/currentThread)) duration-secs))
  (Thread/sleep (* duration-secs 1000))
  (syncout (format "thread %s is done." (.getId (Thread/currentThread)))))

(defn print-thread-progress-and-wait
  [duration-secs]
  (let [start (System/currentTimeMillis)] 
    (loop [now start]
      (let [elapsed (-> now (- start) double (/ 1000) int)]
        (reset! joblib/-ticker (int (* elapsed (/ 100 duration-secs))))
        (syncout (format "thread %s progress is: %s" (.getId (Thread/currentThread)) @joblib/-ticker))
        ;;(when-not (nil? -ticker)
        ;;  (
        (when (< elapsed duration-secs)
          (Thread/sleep 100)
          (recur (System/currentTimeMillis)))))))

(defn gen-fns
  [num-jobs]
  (repeatedly num-jobs #(partial print-thread-progress-and-wait (rand-int 10))))

;;

(def state (atom []))

(defn root
  [_]
  (let [num-items (rand-int 40)
        mkwidget (fn [i]
                   {:fx/type :label
                    :text (str "Hello, world. I am widget " i)})
        ]
    {:fx/type :v-box
     :alignment :center
     :children (mapv mkwidget (range 0 num-items))}))

(defn ui
  []
  (fx/on-fx-thread
   (fx/create-component
    {:fx/type :stage
     :showing true
     :title "Cljfx example"
     :width 300
     :height 100
     :scene {:fx/type :scene
             :root {:fx/type root}}})))

(defn start
  []
  (ui))
