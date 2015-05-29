(ns calfpath.core
  (:require
    [clojure.string :as str]
    [calfpath.internal :as i])
  (:import
    [calfpath Util]))


(defmacro match-route
  "Given a ring request map and pairs of routes (URI pattern string, e.g. '/user/:id/profile/:type/') and expression
  clauses, evaluate matching expression after adding URI params as a map to the :params key in the request map. Odd
  numbered clauses imply the last argument is the default expression invoked on no-match. Even numbered clauses return
  HTTP 400 by default on no-match. The dispatch happens in linear time based on URI length and number of clauses."
  [request & clauses]
  (when-not (symbol? request)
    (throw (IllegalArgumentException.
             (str "Expected a symbol bound to ring request map, but found (" (class request) ") " (pr-str request)))))
  (when-not (#{0 1} (rem (count clauses) 3))
    (throw (IllegalArgumentException.
             (str "Expected clauses in sets of 3 with an optional default expression, but found " (pr-str clauses)))))
  (doseq [[route dav _] (partition 3 clauses)]
    (when-not (string? route)
      (throw (IllegalArgumentException.
               (str "Expected a route string, but found (" (class route) ") " (pr-str route)))))
    (when-not (and (vector? dav) (every? symbol? dav))
      (throw (IllegalArgumentException.
               (str "Expected destructuring argument vector with symbols, but found (" (class dav) ") "
                 (pr-str dav))))))
  (let [response-400 {:status 400
                      :headers {"Content-Type" "text/plain"}
                      :body "400 Bad request. URI does not match any available route."}]
    (if (seq clauses)
      (let [params (gensym "params__")]
        (if (= 1 (count clauses))
          (first clauses)
          (let [[route dav expr] clauses]
            `(if-let [{:keys ~dav :as ~params} (Util/matchURI (:uri ~request) ~(i/parse-route \: route))]
               ;; code commented out below "expensively" merges the URI params to request under the :params key
               ;; (let [~request (assoc ~request :params (merge-with merge (:params ~request) ~params))] ~expr)
               ~expr
               (match-route ~request ~@(drop 3 clauses))))))
      response-400)))


(defmacro match-method
  "Like clojure.core/case except that the first argument must be a request map. Odd numbered clauses imply the last
  argument is the default expression invoked on no-match. Even numbered clauses return HTTP 405 (method not supported)
  by default on no-match. The dispatch happens in constant time."
  [request & clauses]
  (doseq [[method-key _] (partition 2 clauses)]
    (when-not (i/valid-method-keys method-key)
      (throw (IllegalArgumentException.
               (str "Expected method-key to be either of " i/valid-method-keys ", but found (" (class method-key) ") "
                 (pr-str method-key))))))
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