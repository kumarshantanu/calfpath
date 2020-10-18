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
    [calfpath.type :as t]
    #?(:cljs [calfpath.route.uri-token-match-impl :as cljs]))
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
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector [tokens partial?] of
  string and keyword tokens, e.g. [['user' :id 'profile' :descriptor ''] false]. The marker char is ':'."
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


(def ^:const calfpath-context-key "Request key for token context" :calfpath/token-context)


(defn get-calfpath-context
  [request]
  (get request calfpath-context-key))


(defn prepare-request
  [request path-params-key]
  (if (contains? request calfpath-context-key)
    request
    (let [uri-tokens (parse-uri-tokens (:uri request))]
      #?(:cljs (assoc request calfpath-context-key (cljs/make-context uri-tokens))
          :clj (let [path-params (HashMap.)]
                 (-> ^clojure.lang.Associative request
                   (.assoc calfpath-context-key (UriTokenContext. uri-tokens path-params))
                   (.assoc path-params-key      path-params)))))))


(defn update-uri-tokens
  [request uri-tokens path-params-key]
  #?(:cljs (let [context (get-calfpath-context request)]
             (cljs/update-uri-tokens context uri-tokens)
             (assoc request path-params-key (cljs/get-path-params context)))
      :clj (let [^UriTokenContext context (get-calfpath-context request)]
             (.setUriTokens context uri-tokens)
             request)))


;; ----- matcher/matchex support -----


(defn match-static-uri-partial [request static-tokens params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    (cljs/static-uri-partial-match static-tokens))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.staticUriPartialMatch static-tokens)))]
    (update-uri-tokens request rem-tokens params-key)))


(defn match-static-uri-full [request static-tokens params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    (cljs/static-uri-full-match static-tokens))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.staticUriFullMatch static-tokens)))]
    (update-uri-tokens request rem-tokens params-key)))


(defn match-dynamic-uri-partial [request uri-template params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    (cljs/dynamic-uri-partial-match uri-template))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.dynamicUriPartialMatch uri-template)))]
    (update-uri-tokens request rem-tokens params-key)))


(defn match-dynamic-uri-full [request uri-template params-key]
  (when-some [rem-tokens #?(:cljs (-> (get-calfpath-context request)
                                    (cljs/dynamic-uri-full-match uri-template))
                             :clj (-> ^UriTokenContext (get-calfpath-context request)
                                    (.dynamicUriFullMatch uri-template)))]
    (update-uri-tokens request rem-tokens params-key)))


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
