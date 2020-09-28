(defproject calfpath "0.8.0-alpha3"
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
             :cljs {:plugins   [[lein-cljsbuild "1.1.7"]
                                [lein-doo "0.1.10"]]
                    :doo       {:build "test"}
                    :cljsbuild {:builds {:test {:source-paths ["src" "test" "test-doo"]
                                                :compiler {:main          calfpath.runner
                                                           :output-dir    "target/out"
                                                           :output-to     "target/test/core.js"
                                                           :target        :nodejs
                                                           :optimizations :none
                                                           :source-map    true
                                                           :pretty-print  true}}}}
                    :prep-tasks [["cljsbuild" "once"]]
                    :hooks      [leiningen.cljsbuild]}
             :c08 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c09 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :c10 {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :s09 {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.946"]]}
             :s10 {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.10.773"]]}
             :perf {:dependencies [[ataraxy   "0.4.2" :exclusions [[org.clojure/clojure]
                                                                   [ring/ring-core]]]
                                   [bidi      "2.1.6" :exclusions [ring/ring-core]]
                                   [compojure "1.6.2" :exclusions [[org.clojure/clojure]
                                                                   [ring/ring-core]
                                                                   [ring/ring-codec]]]
                                   [metosin/reitit-ring "0.5.5"]
                                   [citius    "0.2.4"]]
                    :test-paths ["perf"]
                    :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m"]}}
  :aliases {"clj-test"  ["with-profile" "c08:c09:c10" "test"]
            "cljs-test" ["with-profile" "cljs,s09:cljs,s10" "doo" "node" "once"]
            "perf-test" ["with-profile" "c10,perf" "test"]})
