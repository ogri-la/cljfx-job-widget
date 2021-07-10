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
        (when (< elapsed duration-secs)
          (Thread/sleep 100) ;; 10 beats a second
          (recur (System/currentTimeMillis)))))))

(defn gen-fns
  [num-jobs]
  (repeatedly num-jobs #(partial print-thread-progress-and-wait (rand-int 10))))

;;

(def state (atom []))

(defn job-listing
  [{:keys [fx/context]}]
  (let [job-queue (fx/sub-val context get-in [:app-state :queue])
        mkwidget (fn [[job-id job-info]]
                   {:fx/type :progress-bar
                    :progress (-> job-info :ticker deref (or 0.0))})
        ]
    {:fx/type :v-box
     :alignment :center
     :children (mapv mkwidget job-queue)}))

(defn button-bar
  [_]
  {:fx/type :v-box
   :children []})

(defn app
  [_]
  {:fx/type :stage
   :showing true
   :title "Cljfx example"
   :width 300
   :height 100
   :scene {:fx/type :scene
           :root {:fx/type :border-pane
                  :top {:fx/type button-bar}
                  :center {:fx/type job-listing}}}})

(defn start
  []
  (let [state-template {:app-state {:queue {}}}
        gui-state (atom (fx/create-context state-template))
        update-gui-state (fn [k a old-state new-state]
                           (swap! gui-state fx/swap-context assoc-in [:app-state :queue] new-state))
        _ (add-watch joblib/-queue :queue-key update-gui-state)

        renderer (fx/create-renderer
                  :middleware (comp
                               fx/wrap-context-desc
                               (fx/wrap-map-desc (fn [_] {:fx/type app})))

                  ;; magic :(

                  :opts {:fx.opt/type->lifecycle #(or (fx/keyword->lifecycle %)
                                                      ;; For functions in `:fx/type` values, pass
                                                      ;; context from option map to these functions
                                                      (fx/fn->lifecycle-with-context %))})
        ]
    (fx/mount-renderer gui-state renderer))
  nil)
