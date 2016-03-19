(ns calfpath.core
  (:require
    [clojure.string :as str]
    [calfpath.internal :as i])
  (:import
    [calfpath Util]))


(defmacro ->uri
  "Given a ring request map and pairs of URI-templates (e.g. '/user/:id/profile/:type/') and expression clauses,
  evaluate matching expression after adding URI params as a map to the :params key in the request map. Odd numbered
  clauses imply the last argument is the default expression invoked on no-match. Even numbered clauses return HTTP 400
  by default on no-match. The dispatch happens in linear time based on URI length and number of clauses."
  [request & clauses]
  (i/expected symbol? "a symbol bound to ring request map" request)
  (when-not (#{0 1} (rem (count clauses) 3))
    (i/expected "clauses in sets of 3 with an optional default expression" clauses))
  (doseq [[uri-template dav _] (partition 3 clauses)]
    (i/expected string? "a uri-template string" uri-template)
    (when-not (and (vector? dav) (every? symbol? dav))
      (i/expected "destructuring argument vector with symbols" dav)))
  (let [response-400 {:status 400
                      :headers {"Content-Type" "text/plain"}
                      :body "400 Bad request. URI does not match any available uri-template."}]
    (if (seq clauses)
      (let [params (gensym "params__")]
        (if (= 1 (count clauses))
          (first clauses)
          (let [[uri-template dav expr] clauses]
            `(if-let [{:keys ~dav :as ~params} (Util/matchURI (:uri ~request) ~(i/parse-uri-template \: uri-template))]
               ;; code commented out below "expensively" merges the URI params to request under the :params key
               ;; (let [~request (assoc ~request :params (merge-with merge (:params ~request) ~params))] ~expr)
               ~expr
               (->uri ~request ~@(drop 3 clauses))))))
      response-400)))


(defn make-uri-handler
  "Given a string URI pattern/parsed template and an arity-2 (request, uri-params map) fn f, return a ring handler that
  invokes f when the request URI matches the URI template, returns nil otherwise. Several such pairs can be passed,
  with an optional arity-1 (request) default handler at the end."
  ([uri-pattern-or-template f]
    (let [uri-template (i/as-uri-template uri-pattern-or-template)]
      (fn [request]
        (when-let [uri-params (Util/matchURI ^String (:uri request) uri-template)]
          (f request uri-params)))))
  ([uri-pattern-or-template f default-handler]
    (let [uri-template (i/as-uri-template uri-pattern-or-template)]
      (fn [request]
        (if-let [uri-params (Util/matchURI ^String (:uri request) uri-template)]
          (f request uri-params)
          (default-handler request)))))
  ([uri-pattern-or-template f uri-pattern-or-template2 g & more]
    (let [clauses   (into [uri-pattern-or-template f uri-pattern-or-template2 g] more)
          templates (->> (partition 2 clauses)
                      (map first)
                      (map i/as-uri-template)
                      object-array)
          handlers  (->> (partition 2 clauses)
                      (map second)
                      object-array)
          n-pairs   (count templates)
          h-default (when (odd? (count clauses))
                      (last clauses))]
      (fn [request]
        (loop [i (int 0)]
          (if (>= i n-pairs)
            (when h-default
              (h-default request))
            (if-let [uri-params (Util/matchURI ^String (:uri request) (aget templates i))]
              ((aget handlers i) request uri-params)
              (recur (unchecked-inc i)))))))))


(defmacro ->method
  "Like clojure.core/case except that the first argument must be a request map. Odd numbered clauses imply the last
  argument is the default expression invoked on no-match. Even numbered clauses return HTTP 405 (method not supported)
  by default on no-match. The dispatch happens in constant time."
  [request & clauses]
  (doseq [[method-key _] (partition 2 clauses)]
    (i/expected i/valid-method-keys (str "method-key to be either of " i/valid-method-keys) method-key))
  (let [valid-method-keys-str (->> (partition 2 clauses)
                                (map first)
                                (map name)
                                (map str/upper-case)
                                (str/join ", "))
        response-405 {:status 405
                      :headers {"Allow"        valid-method-keys-str
                                "Content-Type" "text/plain"}
                      :body (str "405 Method not supported. Supported methods are: " valid-method-keys-str)}]
    (if (seq clauses)
      (if (odd? (count clauses))  ; if odd count, then the user is handling no-match - so no need to provide default
        `(case (:request-method ~request)
           ~@clauses)
        `(case (:request-method ~request)
           ~@clauses
           ~response-405))
      response-405)))


(defmacro ->get
  ([request expr]
    `(i/method-dispatch :get ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :get ~request ~expr ~default-expr)))


(defmacro ->head
  ([request expr]
    `(i/method-dispatch :head ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :head ~request ~expr ~default-expr)))


(defmacro ->options
  ([request expr]
    `(i/method-dispatch :options ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :options ~request ~expr ~default-expr)))


(defmacro ->patch
  ([request expr]
    `(i/method-dispatch :patch ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :patch ~request ~expr ~default-expr)))


(defmacro ->put
  ([request expr]
    `(i/method-dispatch :put ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :put ~request ~expr ~default-expr)))


(defmacro ->post
  ([request expr]
    `(i/method-dispatch :post ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :post ~request ~expr ~default-expr)))


(defmacro ->delete
  ([request expr]
    `(i/method-dispatch :delete ~request ~expr))
  ([request expr default-expr]
    `(i/method-dispatch :delete ~request ~expr ~default-expr)))
