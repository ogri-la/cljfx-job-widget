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
      (let [elapsed (-> now (- start) double (/ 1000) int)
            progress (joblib/progress duration-secs elapsed)]
        (joblib/tick progress)
        ;;(syncout (format "thread %s progress is: %s" (.getId (Thread/currentThread)) (joblib/tick)))
        (when (< elapsed duration-secs)
          (Thread/sleep 100)
          (recur (System/currentTimeMillis)))))))

(defn gen-fns
  [num-jobs]
  (repeatedly num-jobs #(partial print-thread-progress-and-wait (rand-nth (range 2 10)))))

;;

(def state (atom []))

(defn job-listing
  [{:keys [fx/context]}]
  (let [job-queue (fx/sub-val context get-in [:app-state :queue])
        mkwidget (fn [[job-id job-info]]
                   {:fx/type :progress-bar
                    :progress (-> job-info :progress (or 0.0))})
        overall-progress {:fx/type :progress-indicator
                          :progress (joblib/queue-progress job-queue)}]
    {:fx/type :v-box
     :alignment :center
     :children (into (mapv mkwidget job-queue) 
                     [{:fx/type :separator} overall-progress])}))

(defn button-bar
  [_]
  {:fx/type :tool-bar
   :items [
           {:fx/type :button :text "gen 20 jobs" :on-action (fn [_] (run! joblib/create-job-add-to-queue! (gen-fns 20)))}
           {:fx/type :button :text "do all" :on-action (fn [_]
                                                     ;;(swap! joblib/-queue identity))}
                                                     (joblib/start-jobs-in-queue!))}
           {:fx/type :button :text "do 3 at a time" :on-action (fn [_]
                                                                 (joblib/monitor! 3))}
           {:fx/type :button :text "kick" :on-action (fn [_]
                                                       ;; trigger a change to the queue so the monitor starts processing jobs
                                                       (swap! joblib/-queue identity))}
           {:fx/type :button :text "clear all results" :on-action (fn [_] (joblib/pop-all-jobs!))}
            ]
   })

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
