(defproject cljfx-job-widget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name ""
            :url ""}
  :dependencies [[org.clojure/clojure "1.10.3"]

                 [org.clojure/tools.namespace "1.1.0"] ;; reload code
                 [clj-commons/fs "1.5.2"] ;; file system wrangling
                 [org.flatland/ordered "1.5.9"] ;; better ordered map
                 [cljfx "1.7.13" :exclusions [org.openjfx/javafx-web
                                              org.openjfx/javafx-media]]
                 [org.openjfx/javafx-base "15.0.1"]
                 

                 ]

  :main cljfx-job-widget.main
  
  :repl-options {:init-ns cljfx-job-widget.core}
  )
