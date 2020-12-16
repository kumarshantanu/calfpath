;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.uri-index-match
  "Internal namespace to implement URI-index based URI match."
  (:require
    [clojure.string :as string]
    [calfpath.type :as t]
    #?(:cljs [calfpath.route.uri-index-match-impl :as cljs]))
  #?(:clj (:import
            [java.util HashMap]
            [clojure.lang Associative]
            [calfpath.route UriIndexContext])))


(defn parse-uri-template
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector [tokens partial?] of
  alternating string and keyword tokens, e.g. [['/user/' :id '/profile/' :descriptor '/'] false]. Marker char is ':'."
  [uri-pattern]
  (let [pattern-length  (count uri-pattern)
        [path partial?] (if (and (> pattern-length 1)
                              (string/ends-with? uri-pattern "*"))
                          [(subs uri-pattern 0 (dec pattern-length)) true]  ; chop off last char
                          [uri-pattern false])
        n (count path)
        separator \/]
    (loop [i (int 0) ; current index in the URI string
           j (int 0) ; start index of the current token (string or keyword)
           s? true   ; string in progress? (false implies keyword in progress)
           r []]
      (if (>= i n)
        [(conj r (let [t (subs path j i)]
                   (if s?
                     t
                     (keyword t))))
         partial?]
        (let [^char ch  (get path i)
              [jn s? r] (if s?
                          (if (= \: ch)
                            [(unchecked-inc i) false (conj r (subs path j i))]
                            [j true r])
                          (if (= separator ch)
                            [i true  (conj r (keyword (subs path j i)))]
                            [j false r]))]
          (recur (unchecked-inc i) (int jn) s? r))))))


;; ----- request state management -----


(def ^:const calfpath-context-key "Request key for token context" :calfpath/uri-index-context)


(defn get-calfpath-context
  [request]
  (get request calfpath-context-key))


(defn prepare-request
  [request path-params-key]
  (if (contains? request calfpath-context-key)
    request
    #?(:cljs (assoc request calfpath-context-key (cljs/make-context (:uri request)))
        :clj (let [path-params (HashMap.)]
               (-> ^clojure.lang.Associative request
                 (.assoc calfpath-context-key (UriIndexContext. (:uri request) path-params))
                 (.assoc path-params-key      path-params))))))


(defn update-path-params
  "Update request with path params after a successful match."
  [request path-params-key]
  #?(:cljs (->> (get-calfpath-context request)
             cljs/get-path-params
             (assoc request path-params-key))
      :clj request))


;; ----- matcher/matchex support -----


(def ^:const FULL-URI-MATCH-INDEX -1)
(def ^:const NO-URI-MATCH-INDEX -2)


(defn match-static-uri-partial [request static-tokens params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (cljs/static-uri-partial-match (get-calfpath-context request) static-tokens)
              :clj (.staticUriPartialMatch ^UriIndexContext (get-calfpath-context request) static-tokens)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-static-uri-full [request static-tokens params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (cljs/static-uri-full-match (get-calfpath-context request) static-tokens)
              :clj (.staticUriFullMatch ^UriIndexContext (get-calfpath-context request) static-tokens)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-dynamic-uri-partial [request uri-template params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (cljs/dynamic-uri-partial-match (get-calfpath-context request) uri-template)
              :clj (.dynamicUriPartialMatch ^UriIndexContext (get-calfpath-context request) uri-template)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-dynamic-uri-full [request uri-template params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (cljs/dynamic-uri-full-match (get-calfpath-context request) uri-template)
              :clj (.dynamicUriFullMatch ^UriIndexContext (get-calfpath-context request) uri-template)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


;; ----- IRouteMatcher -----


(def route-matcher
  (reify t/IRouteMatcher
    (-parse-uri-template        [_ uri-pattern] (parse-uri-template uri-pattern))
    (-get-static-uri-template   [_ uri-pattern-tokens] (when (and (= 1 (count uri-pattern-tokens))
                                                               (string? (first uri-pattern-tokens)))
                                                         (first uri-pattern-tokens)))
    (-initialize-request        [_ request params-key] (prepare-request request params-key))
    (-static-uri-partial-match  [_ req static-token params-key] (match-static-uri-partial  req static-token params-key))
    (-static-uri-full-match     [_ req static-token params-key] (match-static-uri-full     req static-token params-key))
    (-dynamic-uri-partial-match [_ req uri-template params-key] (match-dynamic-uri-partial req uri-template params-key))
    (-dynamic-uri-full-match    [_ req uri-template params-key] (match-dynamic-uri-full    req uri-template params-key))))
