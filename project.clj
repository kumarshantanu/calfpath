(defproject calfpath "0.8.0-SNAPSHOT"
  :description "A la carte ring request matching"
  :url "https://github.com/kumarshantanu/calfpath"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                *assert* true
                *unchecked-math* :warn-on-boxed}
  :pedantic? :warn
  :java-source-paths ["java-src"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c08 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c09 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :c10 {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :perf {:dependencies [[ataraxy   "0.4.2" :exclusions [[org.clojure/clojure]
                                                                   [ring/ring-core]]]
                                   [bidi      "2.1.5" :exclusions [ring/ring-core]]
                                   [compojure "1.6.1" :exclusions [[org.clojure/clojure]
                                                                   [ring/ring-core]
                                                                   [ring/ring-codec]]]
                                   [metosin/reitit-ring "0.2.10"]
                                   [citius    "0.2.4"]]
                    :test-paths ["perf"]
                    :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m"]}}
  :aliases {"perf-test" ["with-profile" "c10,perf" "test"]})
