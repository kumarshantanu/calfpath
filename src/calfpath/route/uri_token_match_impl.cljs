;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.uri-token-match-impl
  "CLJS implementation for URI-tokens based URI matching.")


(defrecord TokenContext [^:mutable uri-tokens
                         ^:mutable ^long uri-token-count
                         ^:mutable path-params])


(defn make-context
  ^TokenContext [uri-tokens]
  (TokenContext. uri-tokens (count uri-tokens) {}))


(defn get-path-params
  [^IndexContext context]
  (.-path-params context))


(defn update-uri-tokens
  [^IndexContext context uri-tokens]
  (set! (.-uri-tokens      context) uri-tokens)
  (set! (.-uri-token-count context) (count uri-tokens)))


;; ----- URI matching -----


(def FULL-URI-MATCH-TOKENS [])


(defn partial-match
  ([^IndexContext context uri-tokens]
    (set! (.-uri-tokens context) uri-tokens)
    (set! (.-uri-token-count context) (count uri-tokens))
    uri-tokens)
  ([^IndexContext context uri-tokens path-params]
    (set! (.-path-params context) (conj (.-path-params context) path-params))
    (set! (.-uri-tokens context) uri-tokens)
    (set! (.-uri-token-count context) (count uri-tokens))
    uri-tokens))


(defn full-match
  ([^IndexContext context]             (partial-match context FULL-URI-MATCH-TOKENS))
  ([^IndexContext context path-params] (partial-match context FULL-URI-MATCH-TOKENS path-params)))


;; ~~~ match fns ~~~


(defn dynamic-uri-partial-match
  "(Partial) Match URI tokens (vector) against URI-pattern tokens. Optimized for dynamic routes.
  Return (vector of) remaining URI tokens on match, interpreted as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |
  | non-empty | partial match |"
  [^TokenContext context pattern-tokens]
  (let [pattern-token-count (count pattern-tokens)]
    (when (>= (.-uri-token-count context) pattern-token-count)
      (loop [path-params (transient {})
             token-index 0]
        (let [each-uri-token     (get (.-uri-tokens context) token-index)
              each-pattern-token (get pattern-tokens token-index)]
          (if (< token-index pattern-token-count)
            (when-not (and (string? each-pattern-token)
                        (not= each-uri-token each-pattern-token))
              (recur
                (if (string? each-pattern-token)
                  path-params
                  (assoc! path-params each-pattern-token each-uri-token))
                (unchecked-inc token-index)))
            (if (> (.-uri-token-count context) pattern-token-count)
              (partial-match context
                (subvec (.-uri-tokens context) pattern-token-count (.-uri-token-count context))
                (persistent! path-params))
              (full-match context
                (persistent! path-params)))))))))


(defn dynamic-uri-full-match
  "(Full) Match URI tokens (vector) against URI-pattern tokens. Optimized for dynamic routes.
  Return (vector of) remaining URI tokens on match, interpreted as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |"
  [^TokenContext context pattern-tokens]
  (let [pattern-token-count (count pattern-tokens)]
    (when (= (.-uri-token-count context) pattern-token-count)
      (loop [path-params (transient {})
             token-index 0]
        (let [each-uri-token     (get (.-uri-tokens context) token-index)
              each-pattern-token (get pattern-tokens token-index)]
          (if (< token-index pattern-token-count)
            (when-not (and (string? each-pattern-token)
                        (not= each-uri-token each-pattern-token))
              (recur
                (if (string? each-pattern-token)
                  path-params
                  (assoc! path-params each-pattern-token each-uri-token))
                (unchecked-inc token-index)))
            (full-match context (persistent! path-params))))))))


(defn static-uri-partial-match
  "(Full) Match URI tokens against URI-pattern string tokens. Only for static routes.
  Return (vector) remaining-uri-tokens not yet matched - interpret as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |
  | non-empty | partial match |"
  [^TokenContext context static-tokens]
  (let [static-token-count (count static-tokens)]
    (when (>= (.-uri-token-count context) static-token-count)
      (loop [i 0]
        (if (< i static-token-count)
          (when (= (get (.-uri-tokens context) i)
                  (get static-tokens i))
            (recur (unchecked-inc i)))
          (if (> (.-uri-token-count context) static-token-count)
            (partial-match context (subvec (.-uri-tokens context) static-token-count (.-uri-token-count context)))
            (full-match context)))))))


(defn static-uri-full-match
  "(Full) Match URI tokens against URI-pattern string tokens. Only for static routes.
  Return (vector) remaining-uri-tokens not yet matched - interpret as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |"
  [^TokenContext context static-tokens]
  (let [static-token-count (count static-tokens)]
    (when (= (.-uri-token-count context) static-token-count)
      (loop [i 0]
        (if (< i static-token-count)
          (when (= (get (.-uri-tokens context) i)
                  (get static-tokens i))
            (recur (unchecked-inc i)))
          FULL-URI-MATCH-TOKENS)))))
