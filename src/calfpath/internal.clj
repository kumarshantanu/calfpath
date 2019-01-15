;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.internal
  (:require
    [clojure.string :as string])
  (:import
    [java.util Iterator Map Map$Entry]
    [calfpath MatchResult Util]))


(defn expected
  ([expectation found]
    (throw (IllegalArgumentException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


(defn parse-uri-template
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector of alternating string
  and keyword tokens, e.g. ['/user/' :id '/profile/' :descriptor '/']. The marker char is typically ':'."
  [marker-char ^String pattern]
  (let [[^String path partial?] (if (and (> (.length pattern) 1)
                                      (.endsWith pattern "*"))
                                  [(subs pattern 0 (dec (.length pattern))) true]  ; chop off last char
                                  [pattern false])
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
        (let [ch (.charAt path i)
              [jn s? r] (if s?
                          (if (= ^char marker-char ch)
                            [(unchecked-inc i) false (conj r (subs path j i))]
                            [j true r])
                          (if (= separator ch)
                            [i true  (conj r (keyword (subs path j i)))]
                            [j false r]))]
          (recur (unchecked-inc i) (int jn) s? r))))))


(def ^:const default-separator \:)


(defn as-uri-template
  [uri-pattern-or-template]
  (cond
    (string? uri-pattern-or-template)      (parse-uri-template default-separator uri-pattern-or-template)
    (and (vector? uri-pattern-or-template)
      (every? (some-fn string? keyword?)
        uri-pattern-or-template))          uri-pattern-or-template
    :otherwise                             (expected "a string URI pattern or a parsed URI template"
                                             uri-pattern-or-template)))


(def ^:const uri-match-end-index :calfpath/uri-match-end-index)


(definline get-uri-match-end-index
  [request]
  `(or (get ~request uri-match-end-index) 0))


(definline assoc-uri-match-end-index
  [request end-index]
  `(assoc ~request uri-match-end-index ~end-index))


(def path-params :calfpath/path-params)


(def valid-method-keys #{:get :head :options :patch :put :post :delete})


(defmacro method-dispatch
  ([method-keyword request expr]
    (when-not (valid-method-keys method-keyword)
      (expected (str "a method key (" valid-method-keys ")") method-keyword))
    (let [method-string (->> (name method-keyword)
                          string/upper-case)
          default-expr {:status 405
                        :headers {"Allow"        method-string
                                  "Content-Type" "text/plain"}
                        :body (format "405 Method not supported. Only %s is supported." method-string)}]
      `(if (identical? ~method-keyword (:request-method ~request))
         ~expr
         ~default-expr)))
  ([method-keyword request expr default-expr]
    (when-not (valid-method-keys method-keyword)
      (expected (str "a method key (" valid-method-keys ")") method-keyword))
    `(if (identical? ~method-keyword (:request-method ~request))
       ~expr
       ~default-expr)))


(defn conj-maps
  "Merge two maps efficiently using conj."
  [old-map new-map]
  (conj
    (if (nil? old-map)
      {}
      old-map)
    new-map))


(defn reduce-mkv
  "Same as clojure.core/reduce-kv for java.util.Map instances."
  [f init ^Map m]
  (if (or (nil? m) (.isEmpty m))
    init
    (let [i (.iterator (.entrySet m))]
      (loop [last-result init]
        (if (.hasNext ^Iterator i)
          (let [^Map$Entry pair (.next i)]
            (recur (f last-result (.getKey pair) (.getValue pair))))
          last-result)))))


(defn invoke
  "Invoke first arg as a function on remaing args."
  ([f]            (f))
  ([f x]          (f x))
  ([f x y]        (f x y))
  ([f x y & args] (apply f x y args)))


(defn strip-partial-marker
  [x]
  (when (string? x)
    (if (.endsWith ^String x "*")
      (subs x 0 (dec (count x)))
      x)))


;; helpers for `routes -> wildcard trie`


(defn split-routes-having-uri
  "Given mixed routes (vector), split into those having distinct routes and those that don't."
  [routes uri-key]
  (reduce (fn [[with-uri no-uri] each-route]
            (if (and (contains? each-route uri-key)
                  ;; wildcard already? then exclude
                  (not (.endsWith ^String (get each-route uri-key) "*")))
              [(conj with-uri each-route) no-uri]
              [with-uri (conj no-uri each-route)]))
    [[] []] routes))


(defn tokenize-routes-uris
  "Given routes with URI patterns, tokenize them as vectors."
  [routes-with-uri uri-key]
  (mapv (fn [^String uri-template]
          (as-> uri-template $
            (get $ uri-key)
            (string/split $ #"/")
            (mapv #(if (.startsWith ^String % ":")
                     (keyword (subs % 1))
                     %)
              $)))
    routes-with-uri))


(defn find-prefix-tokens
  "Given routes with URI-patterns, find the common (non empty) prefix URI pattern tokens."
  [routes-uri-tokens]
  (reduce (fn [tokens-a tokens-b]
            (->> (map vector tokens-a tokens-b)
              (take-while (fn [[a b]] (= a b)))
              (mapv first)))
    routes-uri-tokens))


(defn dropper
  [n]
  (fn [items] (->> items
                (drop n)
                vec)))


(defn find-prefix-tokens-pair
  "Given routes with URI-patterns, find the common (non empty) prefix URI pattern tokens and balance tokens."
  [routes-uri-tokens]
  (let [prefix-tokens (find-prefix-tokens routes-uri-tokens)]
    (when-not (= [""] prefix-tokens)
      [prefix-tokens (-> (count prefix-tokens)
                       dropper
                       (mapv routes-uri-tokens))])))


(defn find-discriminator-tokens
  [routes-uri-tokens]
  (let [max-cut-count (->> routes-uri-tokens
                        (mapv count)
                        (apply min))
        prefix-tokens (find-prefix-tokens routes-uri-tokens)
        prefix-remain (-> (count prefix-tokens)
                        dropper
                        (mapv routes-uri-tokens))
        delta-tokens  (mapv (comp vector first) prefix-remain)
        suffix-tokens (when true ;(<= (inc (count prefix-tokens)) max-cut-count)
                        (->> prefix-remain
                          (mapv (dropper 1))
                          find-prefix-tokens))
        tok-cut-count (+ (count prefix-tokens) 1 (count suffix-tokens))]
    [(->> delta-tokens
       (mapv #(-> prefix-tokens
                (concat % suffix-tokens)
                vec)))
     tok-cut-count]))


(defn find-discriminator-tokens2
  [routes-uri-tokens]
  (loop [token-count   1
         token-vectors routes-uri-tokens]
    (cond
      (some empty?
        token-vectors)   [nil 0]
      (->> token-vectors
        (map first)
        (apply =))       (recur (inc token-count) (mapv next token-vectors))
      :else              [(->> routes-uri-tokens
                            (mapv #(vec (take token-count %))))
                          token-count])))


(declare triefy-all)


(defn triefy [routes-with-uri trie-threshold uri-key]  ; return vector of routes
  (let [routes-uri-tokens (tokenize-routes-uris routes-with-uri uri-key)  ; [ [t1 t2 ..] [t1 t2 ..] ...]
        [prefix-tokens
         token-vectors]   (find-prefix-tokens-pair routes-uri-tokens)]
    (if (seq prefix-tokens)
      ;; we found a common URI-prefix for all routes
      [{uri-key (-> "/"
                  (string/join prefix-tokens)
                  (str "*"))
        :nested (as-> token-vectors $
                  (mapv (fn [route tokens]
                          (assoc route uri-key (string/join "/" tokens))) routes-with-uri $)
                  (triefy-all $ trie-threshold uri-key))}]
      ;; we need to find URI-prefix groups now
      (let [[first-tokens
             first-count] (find-discriminator-tokens routes-uri-tokens)
            token-counts  (->> routes-uri-tokens
                            (group-by #(take first-count %))
                            (reduce-kv #(assoc %1 %2 (count %3)) {}))]
        (if (->> [routes-with-uri routes-uri-tokens first-tokens]
              (map count)
              (apply =))
          (->> [routes-with-uri routes-uri-tokens first-tokens]
            (apply map vector)
            (sort-by last)
            (partition-by last)
            (reduce (fn [result-routes batch]
                      (if (> (count batch) trie-threshold)
                        (conj result-routes
                          {uri-key (as-> (last (first batch)) $
                                     (string/join "/" $)
                                     (str $ "*"))
                           :nested (as-> batch $
                                     (mapv (fn [[r ts ft]]
                                             (assoc r uri-key (->> (drop first-count ts)
                                                                (string/join "/")
                                                                (str "/")))) $)
                                     (triefy-all $ trie-threshold uri-key))})
                        (->> batch
                          (mapv first)
                          (into result-routes))))
              []))
          routes-with-uri)))))


(defn triefy-all
  [routes trie-threshold uri-key]
  (expected #(> % 1) "value of :trie-threshold must be more than 1" trie-threshold)
  (let [[with-uri no-uri] (split-routes-having-uri routes uri-key)]
    (if (> (count with-uri) trie-threshold)
      (-> (triefy with-uri trie-threshold uri-key)
        vec
        (into no-uri))
      routes)))
