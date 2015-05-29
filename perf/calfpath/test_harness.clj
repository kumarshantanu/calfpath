(ns calfpath.test-harness
  (:require
    [clojure.test :refer :all]
    [clojure.pprint    :as pp]
    [clojure.string    :as t]
    [clansi.core       :as a]
    [cljfreechart.core :as b]
    [criterium.core    :as c]))


(def ^:dynamic *bar-chart-data* nil)


(defn make-bench-test-wrapper
  [bench-chart-filename]
  (fn [f]
    (binding [*bar-chart-data* (atom [])]
      (f)
      (-> (let [raw-data @*bar-chart-data*
                make-row (fn [k] (reduce (fn [x {:keys [fast-name] :as y}]
                                           (assoc x fast-name (get y k)))
                                   {} raw-data))]
            [(assoc (make-row :slow-mean) :name "Core")
             (assoc (make-row :fast-mean) :name "Stringer")])
        (b/make-category-dataset {:group-key :name})
        (b/make-bar-chart-3d "Stringer benchmark statistics (lower is better)" {:category-title "Test cases"
                                                                                :value-title "Latency (lower is better)"})
        (b/save-chart-as-file bench-chart-filename {:width 1280 :height 800})))))


(defn save-bar-chart-data!
  "Save given map as bar-chart data."
  [slow-name slow-mean fast-name fast-mean]
  (swap! *bar-chart-data* conj {:slow-mean slow-mean
                                :fast-mean fast-mean
                                :slow-name slow-name
                                :fast-name fast-name}))


(defmacro measure
  [expr]
  `(do
     (println "\n::::: Benchmarking" ~(pr-str expr))
     (let [result# (c/benchmark ~expr {})]
       [result# (with-out-str (c/report-result result#))])))


(defn nix?
  []
  (let [os (t/lower-case (str (System/getProperty "os.name")))]
    (some #(>= (.indexOf os ^String %) 0) ["mac" "linux" "unix"])))


(defn colorize
  [text & args]
  (if (nix?)
    (apply a/style text args)
    text))


(defn comparison-summary
  [slow-bench fast-bench]
  (let [^double slow-mean (first (:mean slow-bench))
        ^double fast-mean (first (:mean fast-bench))
        diff (Math/abs ^double (- slow-mean fast-mean))
        calc-percent (fn [] (double (/ (* 100 diff)
                                      (if (> slow-mean fast-mean)
                                        fast-mean
                                        slow-mean))))]
    (cond
      (= slow-mean fast-mean) (colorize "Both are equal" :yellow)
      (> slow-mean fast-mean) (colorize (format "Stringer is faster by %.2f%% in this test." (calc-percent) \%)
                                :black :bg-green)
      :otherwise              (colorize (format "Stringer is slower by %.2f%% in this test." (calc-percent) \%)
                                :black :bg-red))))


(defmacro compare-perf
  [slow-expr fast-expr]
  `(do
     (is (= ~slow-expr ~fast-expr))
     (let [[slow-bench# slow-report#] (measure ~slow-expr)
           [fast-bench# fast-report#] (measure ~fast-expr)]
       (let [slow-label# ~(pr-str slow-expr)
             fast-label# ~(pr-str fast-expr)]
         (->> [slow-report# fast-report#]
           (map t/split-lines)
           (apply map (fn [s# f# ] {slow-label# s#
                                    fast-label# f#}))
           (pp/print-table [slow-label# fast-label#])))
       (println (comparison-summary slow-bench# fast-bench#))
       (save-bar-chart-data!
         ~(pr-str slow-expr) (first (:mean slow-bench#))
         ~(pr-str fast-expr) (first (:mean fast-bench#)))
       (is (>= (first (:mean slow-bench#))
             (first (:mean fast-bench#)))))))