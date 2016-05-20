(defproject calfpath "0.3.0-SNAPSHOT"
  :description "A la carte ring request matching"
  :url "https://github.com/kumarshantanu/calfpath"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                *assert* true}
  :java-source-paths ["java-src"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}
             :c15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :perf {:dependencies [[compojure "1.5.0" :exclusions [[org.clojure/clojure]]]
                                   [citius    "0.2.3"]]
                    :test-paths ["perf"]
                    :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m"]}})
