(ns calfpath.internal
  (:require [clojure.string :as str]))


(defn parse-uri-template
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector of alternating string
  and keyword tokens, e.g. ['/user/' :id '/profile/' :descriptor '/']. The marker char is typically ':'."
  [marker-char ^String route]
  (let [n (count route)
        separator \/]
    (loop [i (int 0) ; current index in the URI string
           j (int 0) ; start index of the current token (string or keyword)
           s? true   ; string in progress? (false implies keyword in progress)
           r []]
      (if (>= i n)
        (conj r (let [t (.substring route j i)]
                  (if s?
                    t
                    (keyword t))))
        (let [ch (.charAt route i)
              [jn s? r] (if s?
                          (if (= ^char marker-char ch)
                            [(unchecked-inc i) false (conj r (.substring route j i))]
                            [j true r])
                          (if (= separator ch)
                            [i true  (conj r (keyword (.substring route j i)))]
                            [j false r]))]
          (recur (unchecked-inc i) (int jn) s? r))))))


(defn as-uri-template
  [uri-pattern-or-template]
  (cond
    (string? uri-pattern-or-template)      (parse-uri-template \: uri-pattern-or-template)
    (and (vector? uri-pattern-or-template)
      (every? (some-fn string? keyword?)
        uri-pattern-or-template))          uri-pattern-or-template
    :otherwise (throw (IllegalArgumentException.
                        (str "Expected a string URI pattern or a parsed URI template, but found ("
                          (class uri-pattern-or-template) ") " (pr-str uri-pattern-or-template))))))


(def valid-method-keys #{:get :head :options :patch :put :post :delete})


(defmacro method-dispatch
  ([method-keyword request expr]
    (when-not (valid-method-keys method-keyword)
      (throw (IllegalArgumentException.
               (str "Expected a method key (" valid-method-keys "), but found (" (class method-keyword) ") "
                 (pr-str method-keyword)))))
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
      (throw (IllegalArgumentException.
               (str "Expected a method key (" valid-method-keys "), but found (" (class method-keyword) ") "
                 (pr-str method-keyword)))))
    `(if (identical? ~method-keyword (:request-method ~request))
       ~expr
       ~default-expr)))
