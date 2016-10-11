;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.internal
  (:require
    [clojure.string :as str])
  (:import
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
                          str/upper-case)
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
