(defproject calfpath "0.1.0-SNAPSHOT"
  :description "A la carte ring request matching"
  :url "https://github.com/kumarshantanu/calfpath"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                *assert* true
                *unchecked-math* :warn-on-boxed}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0-RC1"]
                                  [org.clojure/tools.nrepl "0.2.10"]]}
             :perf {:dependencies [[compojure    "1.3.4"]
                                   [criterium    "0.4.3" :exclusions [[org.clojure/clojure] [clojure-complete]]]
                                   [cljfreechart "0.1.1"]
                                   [myguidingstar/clansi "1.3.0" :exclusions [[org.clojure/clojure]]]]
                    :test-paths ["perf"]
                    :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m"]}})