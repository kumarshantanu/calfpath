;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.uri-token-match
  "Internal namespace to implement URI-tokens based URI match."
  (:require
    [clojure.string :as string]
    [calfpath.type :as t])
  #?(:clj (:import
            [java.util HashMap]
            [clojure.lang Associative]
            [calfpath.route UriTokenContext])))


;; ----- URI and URI-template parsing -----


(defn parse-uri-tokens*
  [uri]
  (let [uri-len (count uri)]
    (loop [tcoll (transient [])
           token ""
           index 1]
      (if (< index uri-len)
        (let [ch (get uri index)]
          (if (= ch \/)
            (recur (conj! tcoll token) "" (unchecked-inc index))
            (recur tcoll (str token ch) (unchecked-inc index))))
        (persistent! (conj! tcoll token))))))


(defn parse-uri-tokens
  [uri]
  #?(:cljs (parse-uri-tokens* uri)
      :clj (UriTokenContext/parseUriTokens uri)))


(defn parse-uri-template
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector of alternating string
  and keyword tokens, e.g. [['user' :id 'profile' :descriptor ''] false]. The marker char is ':'."
  [uri-pattern]
  (let [pattern-length  (count uri-pattern)
        [path partial?] (if (and (> pattern-length 1)
                              (string/ends-with? uri-pattern "*"))
                          [(subs uri-pattern 0 (dec pattern-length)) true]  ; chop off last wildcard char
                          [uri-pattern false])
        tokens (cond
                 (= "" path) []
                 (string/starts-with?
                   path "/") (parse-uri-tokens path)
                 :otherwise  (throw (ex-info (str "Expected URI pattern to start with '/', but found "
                                               (pr-str path)) {:path path})))]
    (as-> tokens <>
      (mapv (fn [t]
              (if (string/starts-with? t ":")
                (keyword (subs t 1))
                t)) <>)
      (vector <> partial?))))


;; ----- request state management -----


(defprotocol ITokenContext
  (-get-uri-tokens   [this])
  (-get-path-params  [this])
  (-set-uri-tokens!  [this new-tokens])
  (-add-path-params! [this new-params]))


#?(:cljs (defrecord TokenContext [^:mutable uri-tokens ^:mutable path-params]))


#?(:cljs (extend-protocol ITokenContext
           TokenContext  ; CLJS record type
           (-get-uri-tokens   [this] (.-uri-tokens this))
           (-get-path-params  [this] (.-path-params this))
           (-set-uri-tokens!  [this new-tokens] (set! (.-uri-tokens this) new-tokens))
           (-add-path-params! [this new-params] (set! (.-path-params this) (conj (.-path-params this) new-params))))
    :clj (extend-protocol ITokenContext
           UriTokenContext  ; Java class
           (-get-uri-tokens   [this] (.-uriTokens this))
           (-get-path-params  [this] (.-paramsMap this))
           (-set-uri-tokens!  [this new-tokens] (.setUriTokens this new-tokens))
           (-add-path-params! [this new-params] (.putAll (.-paramsMap this) new-params))))


(def ^:const calfpath-context-key "Request key for token context" :calfpath/token-context)


(defn get-calfpath-context
  [request]
  (get request calfpath-context-key))


(defn prepare-request
  [request path-params-key]
  (if (contains? request calfpath-context-key)
    request
    (let [uri-tokens (parse-uri-tokens (:uri request))]
      #?(:cljs (let [path-params {}]
                 (-> request
                   (assoc calfpath-context-key (TokenContext. uri-tokens path-params))))
          :clj (let [path-params (HashMap.)]
                 (-> ^clojure.lang.Associative request
                   (.assoc calfpath-context-key (UriTokenContext. uri-tokens path-params))
                   (.assoc path-params-key      path-params)))))))


(defn partial-match-update-request
  "Update request with remaining URI tokens after a successful match."
  [request uri-tokens path-params-key]
  #?(:cljs (let [calfpath-context (get-calfpath-context request)]
             (-set-uri-tokens! calfpath-context uri-tokens)
             (assoc request path-params-key (-get-path-params calfpath-context)))
      :clj (do
             (-set-uri-tokens! (get-calfpath-context request) uri-tokens)
             request)))


(defn full-match-update-request
  "Update request with remaining URI tokens after a successful match."
  [request uri-tokens path-params-key]
  #?(:cljs (assoc request path-params-key (-get-path-params (get-calfpath-context request)))
      :clj request))


;; ----- URI matching -----


(defn dynamic-uri-partial-match
  "(Partial) Match URI tokens (vector) against URI-pattern tokens. Optimized for dynamic routes.
  Return (vector of) remaining URI tokens on match, interpreted as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |
  | non-empty | partial match |"
  [uri-tokens pattern-tokens params-map-holder]
  (let [uri-token-count (count uri-tokens)
        pattern-token-count (count pattern-tokens)]
    (when (>= uri-token-count pattern-token-count)
      (loop [path-params (transient {})
             token-index 0]
        (let [each-uri-token     (get uri-tokens token-index)
              each-pattern-token (get pattern-tokens token-index)]
          (if (< token-index pattern-token-count)
            (when-not (and (string? each-pattern-token)
                        (not= each-uri-token each-pattern-token))
              (recur
                (if (string? each-pattern-token)
                  path-params
                  (assoc! path-params each-pattern-token each-uri-token))
                (unchecked-inc token-index)))
            (let [tokens-remaining (if (> uri-token-count pattern-token-count)
                                     (subvec uri-tokens pattern-token-count uri-token-count)
                                     [])]
              ;; mutate path-params on success
              (-add-path-params! params-map-holder (persistent! path-params))
              tokens-remaining)))))))


(defn dynamic-uri-full-match
  "(Full) Match URI tokens (vector) against URI-pattern tokens. Optimized for dynamic routes.
  Return (vector of) remaining URI tokens on match, interpreted as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |"
  [uri-tokens pattern-tokens params-map-holder]
  (let [uri-token-count (count uri-tokens)
        pattern-token-count (count pattern-tokens)]
    (when (= uri-token-count pattern-token-count)
      (loop [path-params (transient {})
             token-index 0]
        (let [each-uri-token     (get uri-tokens token-index)
              each-pattern-token (get pattern-tokens token-index)]
          (if (< token-index pattern-token-count)
            (when-not (and (string? each-pattern-token)
                        (not= each-uri-token each-pattern-token))
              (recur
                (if (string? each-pattern-token)
                  path-params
                  (assoc! path-params each-pattern-token each-uri-token))
                (unchecked-inc token-index)))
            (do
              ;; mutate path-params on success
              (-add-path-params! params-map-holder (persistent! path-params))
              [])))))))


(defn static-uri-partial-match
  "(Full) Match URI tokens against URI-pattern string tokens. Only for static routes.
  Return (vector) remaining-uri-tokens not yet matched - interpret as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |
  | non-empty | partial match |"
  [uri-tokens pattern-tokens]
  (let [uri-token-count (count uri-tokens)
        pattern-token-count (count pattern-tokens)]
    (when (>= uri-token-count pattern-token-count)
      (loop [i 0]
        (if (< i pattern-token-count)
          (when (= (get uri-tokens i)
                  (get pattern-tokens i))
            (recur (unchecked-inc i)))
          (if (> uri-token-count pattern-token-count)
            (subvec uri-tokens pattern-token-count uri-token-count)
            []))))))


(defn static-uri-full-match
  "(Full) Match URI tokens against URI-pattern string tokens. Only for static routes.
  Return (vector) remaining-uri-tokens not yet matched - interpret as follows:

  | Condition | Meaning       |
  |-----------|---------------|
  | `nil`     | no match      |
  | empty     | full match    |"
  [uri-tokens pattern-tokens]
  (let [uri-token-count (count uri-tokens)
        pattern-token-count (count pattern-tokens)]
    (when (= uri-token-count pattern-token-count)
      (loop [i 0]
        (if (< i pattern-token-count)
          (when (= (get uri-tokens i)
                  (get pattern-tokens i))
            (recur (unchecked-inc i)))
          [])))))


;; ----- matcher/matchex support -----


(defn match-static-uri-partial [request static-tokens params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    -get-uri-tokens
                                    (static-uri-partial-match static-tokens))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.staticUriPartialMatch static-tokens)))]
    (partial-match-update-request request rem-tokens params-key)))


(defn match-static-uri-full [request static-tokens params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    -get-uri-tokens
                                    (static-uri-full-match static-tokens))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.staticUriFullMatch static-tokens)))]
    (full-match-update-request request rem-tokens params-key)))


(defn match-dynamic-uri-partial [request uri-template params-key]
  (when-some [rem-tokens #?(:cljs (let [calfpath-context (get-calfpath-context request)]
                                    (-> calfpath-context
                                      -get-uri-tokens
                                      (dynamic-uri-partial-match uri-template calfpath-context)))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.dynamicUriPartialMatch uri-template)))]
    (partial-match-update-request request rem-tokens params-key)))


(defn match-dynamic-uri-full [request uri-template params-key]
  (when-some [rem-tokens #?(:cljs (let [calfpath-context (get-calfpath-context request)]
                                    (-> calfpath-context
                                      -get-uri-tokens
                                      (dynamic-uri-full-match uri-template calfpath-context)))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.dynamicUriFullMatch uri-template)))]
    (full-match-update-request request rem-tokens params-key)))


;; ----- IRouteMatcher -----


(def route-matcher
  (reify t/IRouteMatcher
    (-parse-uri-template        [_ uri-pattern] (parse-uri-template uri-pattern))
    (-get-static-uri-template   [_ uri-pattern-tokens] (when (every? string? uri-pattern-tokens)
                                                         uri-pattern-tokens))
    (-initialize-request        [_ request params-key] (prepare-request request params-key))
    (-static-uri-partial-match  [_ req static-tokens params-key] (match-static-uri-partial  req static-tokens params-key))
    (-static-uri-full-match     [_ req static-tokens params-key] (match-static-uri-full     req static-tokens params-key))
    (-dynamic-uri-partial-match [_ req uri-template  params-key] (match-dynamic-uri-partial req uri-template  params-key))
    (-dynamic-uri-full-match    [_ req uri-template  params-key] (match-dynamic-uri-full    req uri-template  params-key))))
