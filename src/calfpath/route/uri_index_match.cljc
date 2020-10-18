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
    [calfpath.type :as t])
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


(defprotocol IIndexContext
  (-get-uri-begin-index  [this])
  (-get-path-params      [this])
  (-set-uri-begin-index! [this new-index])
  (-add-path-params!     [this new-params]))


#?(:cljs (defrecord IndexContext [uri ^long uri-length
                                  ^:mutable ^long uri-begin-index
                                  ^:mutable path-params]))


#?(:cljs (extend-protocol IIndexContext
           IndexContext  ; CLJS record type
           (-get-uri-begin-index  [this] (.-uri-begin-index this))
           (-get-path-params      [this] (.-path-params this))
           (-set-uri-begin-index! [this new-index] (set! (.-uri-begin-index this) new-index))
           (-add-path-params! [this new-params] (set! (.-path-params this) (conj (.-path-params this) new-params))))
    :clj (extend-protocol IIndexContext
           UriIndexContext  ; Java class
           (-get-uri-begin-index  [this] (.-uriBeginIndex this))
           (-get-path-params      [this] (.-paramsMap this))
           (-set-uri-begin-index! [this new-index]  (.setUriBeginIndex this new-index))
           (-add-path-params!     [this new-params] (.putAll (.-paramsMap this) new-params))))


(def ^:const calfpath-context-key "Request key for token context" :calfpath/uri-index-context)


(defn get-calfpath-context
  [request]
  (get request calfpath-context-key))


(defn prepare-request
  [request path-params-key]
  (if (contains? request calfpath-context-key)
    request
    #?(:cljs (let [uri (:uri request)
                   urilen (count uri)
                   path-params {}]
               (-> request
                 (assoc calfpath-context-key (IndexContext. uri urilen 0 path-params))))
        :clj (let [path-params (HashMap.)]
               (-> ^clojure.lang.Associative request
                 (.assoc calfpath-context-key (UriIndexContext. (:uri request) path-params))
                 (.assoc path-params-key      path-params))))))


(defn update-path-params
  "Update request with path params after a successful match."
  [request path-params-key]
  #?(:cljs (->> (get-calfpath-context request)
             -get-path-params
             (assoc request path-params-key))
      :clj request))


;; ----- URI matching -----


(def ^:const FULL-URI-MATCH-INDEX -1)
(def ^:const NO-URI-MATCH-INDEX -2)


#?(:cljs (defn partial-uri-match
           ([^IndexContext context uri-index]
             (set! (.-uri-begin-index context) uri-index)
             uri-index)
           ([^IndexContext context path-params uri-index]
             (set! (.-uri-begin-index context) uri-index)
             (set! (.-path-params context) (conj (.-path-params context) path-params))
             uri-index)))


#?(:cljs (defn full-uri-match
           ([^IndexContext context]
             (set! (.-uri-begin-index context) (.-uri-length context))
             FULL-URI-MATCH-INDEX)
           ([^IndexContext context path-params]
             (set! (.-uri-begin-index context) (.-uri-length context))
             (set! (.-path-params context) (conj (.-path-params context) path-params))
             FULL-URI-MATCH-INDEX)))


#?(:cljs (defn remaining-uri
           ([^IndexContext context]           (subs (.-uri context) (.-uri-begin-index context)))
           ([^IndexContext context uri-index] (subs (.-uri context) uri-index))))


;; ~~~ match fns ~~~


#?(:cljs (defn dynamic-uri-match
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
                   (full-uri-match context (persistent! path-params))))))))


#?(:cljs (defn dynamic-uri-partial-match
           [^IndexContext context pattern-tokens]
           (dynamic-uri-match context pattern-tokens true)))


#?(:cljs (defn dynamic-uri-full-match
           [^IndexContext context pattern-tokens]
           (dynamic-uri-match context pattern-tokens false)))


#?(:cljs (defn static-uri-partial-match
           [^IndexContext context static-token]
           (let [rem-uri (remaining-uri context)
                 rem-len (count rem-uri)]
             (if (string/starts-with? rem-uri static-token)
               (let [token-length (count static-token)]
                 (if (= rem-len token-length)
                   (full-uri-match    context)
                   (partial-uri-match context (unchecked-add (.-uri-begin-index context) token-length))))
               NO-URI-MATCH-INDEX))))


#?(:cljs (defn static-uri-full-match
           [^IndexContext context static-token]
           (if (= 0 (.-uri-begin-index context))
             (if (= static-token (.-uri context))
               FULL-URI-MATCH-INDEX
               NO-URI-MATCH-INDEX)
             (let [rem-uri (remaining-uri context)
                   rem-len (count rem-uri)]
               (if (= rem-uri static-token)
                 FULL-URI-MATCH-INDEX
                 NO-URI-MATCH-INDEX)))))


;; ----- matcher/matchex support -----


(defn match-static-uri-partial [request static-tokens params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (static-uri-partial-match (get-calfpath-context request) static-tokens)
              :clj (.staticUriPartialMatch ^UriIndexContext (get-calfpath-context request) static-tokens)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-static-uri-full [request static-tokens params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (static-uri-full-match (get-calfpath-context request) static-tokens)
              :clj (.staticUriFullMatch ^UriIndexContext (get-calfpath-context request) static-tokens)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-dynamic-uri-partial [request uri-template params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (dynamic-uri-partial-match (get-calfpath-context request) uri-template)
              :clj (.dynamicUriPartialMatch ^UriIndexContext (get-calfpath-context request) uri-template)))
    #?(:cljs (update-path-params request params-key)
        :clj request)))


(defn match-dynamic-uri-full [request uri-template params-key]
  (when (not= NO-URI-MATCH-INDEX
          #?(:cljs (dynamic-uri-full-match (get-calfpath-context request) uri-template)
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
