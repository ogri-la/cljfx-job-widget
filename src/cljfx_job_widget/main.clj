(ns cljfx-job-widget.main
  (:require
   [clojure.tools.namespace.repl :as tn :refer [refresh]]
   [cljfx-job-widget.core :as core]))

(defn main
  [& args]
  (core/start args))
