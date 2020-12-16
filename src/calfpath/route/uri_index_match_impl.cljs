;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.uri-index-match-impl
  "CLJS implementation for URI-index based URI matching."
  (:require
    [clojure.string :as string]))


(defrecord IndexContext [#_immutable uri
                         #_immutable ^long uri-length
                         ^:mutable   ^long uri-begin-index
                         ^:mutable   path-params])


(defn make-context
  ^IndexContext [uri]
  (IndexContext. uri (count uri) 0 {}))


(defn get-path-params
  [^IndexContext context]
  (.-path-params context))


;; ----- URI matching -----


(def ^:const FULL-URI-MATCH-INDEX -1)
(def ^:const NO-URI-MATCH-INDEX -2)


(defn partial-uri-match
  ([^IndexContext context uri-index]
    (set! (.-uri-begin-index context) uri-index)
    uri-index)
  ([^IndexContext context path-params uri-index]
    (set! (.-uri-begin-index context) uri-index)
    (set! (.-path-params context) (conj (.-path-params context) path-params))
    uri-index))


(defn full-uri-match
  ([^IndexContext context]
    (set! (.-uri-begin-index context) (.-uri-length context))
    FULL-URI-MATCH-INDEX)
  ([^IndexContext context path-params]
    (set! (.-uri-begin-index context) (.-uri-length context))
    (set! (.-path-params context) (conj (.-path-params context) path-params))
    FULL-URI-MATCH-INDEX))


(defn remaining-uri
  ([^IndexContext context]           (subs (.-uri context) (.-uri-begin-index context)))
  ([^IndexContext context uri-index] (subs (.-uri context) uri-index)))


;; ~~~ match fns ~~~


(defn dynamic-uri-match
  [^IndexContext context pattern-tokens partial?]
  (if (>= (.-uri-begin-index context) (.-uri-length context))
    NO-URI-MATCH-INDEX
    (loop [uri-index   (.-uri-begin-index context)
           path-params (transient {})
           next-tokens (seq pattern-tokens)]
      (if next-tokens
        (if (>= uri-index (.-uri-length context))
          (if partial?
            (partial-uri-match context (persistent! path-params) (.-uri-length context))
            NO-URI-MATCH-INDEX)
          (let [token (first next-tokens)]
            (if (string? token)
              ;; string token
              (if (string/starts-with? (remaining-uri context uri-index) token)
                (recur (unchecked-add uri-index (count token)) path-params (next next-tokens))
                NO-URI-MATCH-INDEX)
              ;; must be a keyword
              (let [[pp ui] (loop [sb ""    ; string buffer
                                   j uri-index]
                              (if (< j (.-uri-length context))
                                (let [ch (get (.-uri context) j)]
                                  (if (= \/ ch)
                                    [(assoc! path-params token sb)
                                     j]
                                    (recur (str sb ch) (unchecked-inc j))))
                                [(assoc! path-params token sb)
                                 (.-uri-length context)]))]
                (recur ui pp (next next-tokens))))))
        (if (< uri-index (.-uri-length context))
          (if partial?
            (partial-uri-match context (persistent! path-params) uri-index)
            NO-URI-MATCH-INDEX)
          (full-uri-match context (persistent! path-params)))))))


(defn dynamic-uri-partial-match
  [^IndexContext context pattern-tokens]
  (dynamic-uri-match context pattern-tokens true))


(defn dynamic-uri-full-match
  [^IndexContext context pattern-tokens]
  (dynamic-uri-match context pattern-tokens false))


(defn static-uri-partial-match
  [^IndexContext context static-token]
  (let [rem-uri (remaining-uri context)
        rem-len (count rem-uri)]
    (if (string/starts-with? rem-uri static-token)
      (let [token-length (count static-token)]
        (if (= rem-len token-length)
          (full-uri-match    context)
          (partial-uri-match context (unchecked-add (.-uri-begin-index context) token-length))))
      NO-URI-MATCH-INDEX)))


(defn static-uri-full-match
  [^IndexContext context static-token]
  (if (= 0 (.-uri-begin-index context))
    (if (= static-token (.-uri context))
      FULL-URI-MATCH-INDEX
      NO-URI-MATCH-INDEX)
    (let [rem-uri (remaining-uri context)
          rem-len (count rem-uri)]
      (if (= rem-uri static-token)
        FULL-URI-MATCH-INDEX
        NO-URI-MATCH-INDEX))))
