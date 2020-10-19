;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.uri-match
  "Internal namespace for data-driven route URI match."
  (:require
    [clojure.string :as string])
  #?(:clj (:import
            [java.util HashMap]
            [clojure.lang Associative]
            [calfpath.route UriMatch])))


(def ^:const FULL-URI-MATCH-INDEX "URI matches fully"  -1)
(def ^:const NO-URI-MATCH-INDEX   "URI does not match" -2)
(def ^:const LO-URI-MATCH-INDEX   "URI is lower"       -3)
(def ^:const HI-URI-MATCH-INDEX   "URI is higher"      -4)


(defn partial-uri-match
  [params-map path-params uri-index]
  (vswap! params-map conj path-params)
  uri-index)


(defn full-uri-match
  [params-map path-params]
  (vswap! params-map conj path-params)
  FULL-URI-MATCH-INDEX)


;; ~~~ match fns ~~~


(defn dynamic-uri-match
  [uri begin-index params-map pattern-tokens partial?]
  (let [uri-length (count uri)]
    (if (>= begin-index uri-length)
      NO-URI-MATCH-INDEX
      (loop [uri-index   begin-index
             path-params (transient {})
             next-tokens (seq pattern-tokens)]
        (if next-tokens
          (if (>= uri-index uri-length)
            (if partial?
              (partial-uri-match params-map (persistent! path-params) uri-length)
              NO-URI-MATCH-INDEX)
            (let [token (first next-tokens)]
              (if (string? token)
                ;; string token
                (if (string/starts-with? (subs uri uri-index) token)
                  (recur (unchecked-add uri-index (count token)) path-params (next next-tokens))
                  NO-URI-MATCH-INDEX)
                ;; must be a keyword
                (let [[pp ui] (loop [sb ""    ; string buffer
                                     j uri-index]
                                (if (< j uri-length)
                                  (let [ch (get uri j)]
                                    (if (= \/ ch)
                                      [(assoc! path-params token sb)
                                       j]
                                      (recur (str sb ch) (unchecked-inc j))))
                                  [(assoc! path-params token sb)
                                   uri-length]))]
                  (recur ui pp (next next-tokens))))))
          (if (< uri-index uri-length)
            (if partial?
              (partial-uri-match params-map (persistent! path-params) uri-index)
              NO-URI-MATCH-INDEX)
            (full-uri-match params-map (persistent! path-params))))))))


(defn dynamic-uri-partial-match*
  [uri ^long begin-index params-map pattern-tokens]
  (dynamic-uri-match uri begin-index params-map pattern-tokens true))


(defn dynamic-uri-full-match*
  [uri ^long begin-index params-map pattern-tokens]
  (dynamic-uri-match uri begin-index params-map pattern-tokens false))


(defn static-uri-partial-match*
  [uri ^long begin-index static-token]
  (let [rem-uri (subs uri begin-index)
        rem-len (count rem-uri)]
    (if (string/starts-with? rem-uri static-token)
      (let [token-length (count static-token)]
        (if (= rem-len token-length)
          FULL-URI-MATCH-INDEX
          #_partial-match (unchecked-add begin-index token-length)))
      NO-URI-MATCH-INDEX)))


(defn static-uri-full-match*
  [uri ^long begin-index static-token]
  (if (= 0 begin-index)
    (if (= static-token uri)
      FULL-URI-MATCH-INDEX
      NO-URI-MATCH-INDEX)
    (let [rem-uri (subs uri begin-index)
          rem-len (count rem-uri)]
      (if (= rem-uri static-token)
        FULL-URI-MATCH-INDEX
        NO-URI-MATCH-INDEX))))


;; ----- matcher / matchex support -----


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


(defn get-static-uri-template
  [uri-pattern-tokens]
  (when (and (= 1 (count uri-pattern-tokens))
          (string? (first uri-pattern-tokens)))
    (first uri-pattern-tokens)))


(def ^:const uri-begin-index-key :calfpath/uri-begin-index)


(defn static-uri-partial-match [request static-token params-key]
  (let [^String uri (:uri request)
        begin-index (uri-begin-index-key request 0)
        final-index #?(:cljs (static-uri-partial-match*      uri begin-index static-token)
                        :clj (UriMatch/staticUriPartialMatch uri begin-index static-token))]
    (when (not= NO-URI-MATCH-INDEX final-index)
      (assoc request uri-begin-index-key (if (= final-index FULL-URI-MATCH-INDEX)
                                           #?(:cljs (count uri)
                                               :clj (.length uri))
                                           final-index)))))


(defn static-uri-full-match [request static-token params-key]
  (let [uri (:uri request)
        begin-index (uri-begin-index-key request 0)
        final-index #?(:cljs (static-uri-full-match*      uri begin-index static-token)
                        :clj (UriMatch/staticUriFullMatch uri begin-index static-token))]
    (when (not= NO-URI-MATCH-INDEX final-index)
      request)))


(defn dynamic-uri-partial-match [request uri-template params-key]
  (let [^String uri (:uri request)
        begin-index (uri-begin-index-key request 0)
        has-params? (contains? request params-key)
        path-params #?(:cljs (volatile! (if (true? has-params?) (get request params-key) {}))
                        :clj (if (true? has-params?) (get request params-key) (HashMap.)))
        final-index #?(:cljs (dynamic-uri-partial-match*      uri begin-index path-params uri-template)
                        :clj (UriMatch/dynamicUriPartialMatch uri begin-index path-params uri-template))]
    (when (not= NO-URI-MATCH-INDEX final-index)
      #?(:cljs (-> request
                 (assoc uri-begin-index-key (if (= final-index FULL-URI-MATCH-INDEX)
                                              (count uri)
                                              final-index))
                 (assoc params-key          @path-params))
          :clj (let [begin-index (if (= final-index FULL-URI-MATCH-INDEX)
                                   (.length uri)
                                   final-index)]
                 (if (true? has-params?)
                   (-> ^Associative request
                     (.assoc uri-begin-index-key begin-index))
                   (-> ^Associative request
                     (.assoc uri-begin-index-key begin-index)
                     (.assoc params-key          path-params))))))))


(defn dynamic-uri-full-match [request uri-template params-key]
  (let [uri (:uri request)
        begin-index (uri-begin-index-key request 0)
        has-params? (contains? request params-key)
        path-params #?(:cljs (volatile! (if (true? has-params?) (get request params-key) {}))
                        :clj (if (true? has-params?) (get request params-key) (HashMap.)))
        final-index #?(:cljs (dynamic-uri-full-match*      uri begin-index path-params uri-template)
                        :clj (UriMatch/dynamicUriFullMatch uri begin-index path-params uri-template))]
    (when (not= NO-URI-MATCH-INDEX final-index)
      #?(:cljs (assoc request params-key @path-params)
          :clj (if (true? has-params?)
                 request
                 (.assoc ^Associative request params-key path-params))))))
