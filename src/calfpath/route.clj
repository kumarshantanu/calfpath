;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route
  (:require
    [calfpath.internal :as i])
  (:import
    [java.util Map]
    [calfpath Util]))


;; Implementation notes:
;; --------------------
;; A naive implementation would recursively walk the routes evaluating the request with each matcher.
;; The commented out below implements the naive idea:
;
;(defn dispatch
;  ([routes original-request original-params] ; routes must be a vector
;    (let [n (count routes)]
;      (loop [i 0]
;        (when (< i n)
;          (let [route-spec (get routes i)
;                matcher (get route-spec :matcher)
;                match-result (matcher original-request)]
;            (if (nil? match-result)
;              (recur (unchecked-inc i))
;              (let [request (get match-result :request original-request)
;                    params  (merge original-params
;                              (get match-result :params))]
;                (if-let [nested (get route-spec :nested)]
;                  (dispatch nested request params)
;                  (if-let [handler (get route-spec :handler)]
;                    (handler request params)
;                    (i/expected ":nested or :handler key to be present in route" route-spec))))))))))
;  ([routes original-request]
;    (dispatch routes original-request {})))
;
;; Below is the outline of a loop-unrolled optimized version that returns a function that recursively matches routes
;; against the request:
;
;(let [m0 (:matcher (get routes 0))
;      h0 (if-let [nested (:nested (get routes 0))]
;           (make-handler nested)
;           (:handler (get routes 0)))
;      m1 ; similar to m0, for element 1
;      h1 ; similar to h0, for element 1
;      ....]
;  (fn dispatcher
;    ([original-request original-params]
;      (if-let [{:keys [request params]
;                :or {request original-request}} (m0 original-request)]
;        (h0 request (merge original-params params))
;        (if-let [....] ; similar to m0, for element 1
;          .... ; similar to h0, for element 1
;          ....)))
;    ([request]
;      (dispatcher request {}))))
;
;; The `make-dispatcher` function analyzes the routes collection and prepares a loop-unrolled form that is
;; evaluated at the end to create the Ring handler function.


(def ^:dynamic
  *routes* :foo)


(defn make-dispatcher
  "Given a collection of routes return a Ring handler function that matches specified request and invokes
  corresponding route handler on successful match, returns nil otherwise.
  Synopsis:
  0. A route is a map {:matcher arity-1 fn  ; :matcher is a required key
                       :nested  route-map   ; either of :nested and :handler keys must be present
                       :handler arity-3 fn}
  1. A matcher is arity-1 fn (accepts request as argument) that returns {:request request :params route-param-map}
     on successful match, nil otherwise. A matcher may update the request (on successful match) before passing it on.
  2. Handler is arity-2 function (accepts request and match-param-map as arguments)."
  [routes]
  (let [routes (->> routes
                 (map (fn [spec]
                        (when-not (:matcher spec)
                          (i/expected ":matcher key to be present" spec))
                        (cond
                          (contains? spec :handler) spec
                          (contains? spec :nested)  (assoc spec
                                                      :handler (make-dispatcher (:nested spec)))
                          :otherwise                (i/expected ":nested or :handler key to be present in route"
                                                      spec))))
                 vec)
        routes-sym   (gensym "routes-")
        dispatch-sym (gensym "dispatch-")
        request-sym  (gensym "request-")
        params-sym   (vary-meta (gensym "params-")
                       assoc :tag "java.util.Map")
        n            (count routes)
        matcher-syms (mapv (fn [idx] (gensym (str "matcher-" idx "-"))) (range n))
        handler-syms (mapv (fn [idx] (gensym (str "handler-" idx "-"))) (range n))
        bindings (->> (range n)
                   (mapcat (fn [idx]
                             `[~(get matcher-syms idx) (:matcher (get ~routes-sym ~idx))
                               ~(get handler-syms idx) (:handler (get ~routes-sym ~idx))]))
                   ;; eval forms can only access information via root-level vars
                   ;; so we use the dynamic var *routes* here
                   (into `[~routes-sym ~'calfpath.route/*routes*]))
        all-exps (->> (range n)
                   reverse
                   (reduce (fn
                             ([expr]
                               expr)
                             ([expr idx]
                               (let [matcher-sym (get matcher-syms idx)
                                     handler-sym (get handler-syms idx)]
                                 `(if-let [match# (~matcher-sym ~request-sym)]
                                    (let [request# (get match# :request ~request-sym)
                                          ^Map params#  (get match# :params)]
                                      (~handler-sym request#
                                        (if params#
                                          (cond
                                            (.isEmpty ~params-sym) params#
                                            (.isEmpty params#)     ~params-sym
                                            :otherwise             (conj {} ~params-sym params#))
                                          ~params-sym)))
                                    ~expr))))
                     `nil))
        fn-form  `(let [~@bindings]
                    (fn ~dispatch-sym
                      ([~request-sym ~params-sym]
                        ~all-exps)
                      ([~request-sym]
                        (~dispatch-sym ~request-sym {}))))]
    (binding [*routes* routes]
      (eval fn-form))))


(defn conj-fallback-match
  "Given a route vector append a matcher that always matches with a corresponding specified handler."
  [routes handler]
  (conj routes {:matcher (fn [_] {})
                :handler handler}))


(defn conj-fallback-400
  [routes]
  (conj-fallback-match routes
    (fn [_ _]
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body "400 Bad request. URI does not match any available uri-template."})))


(defn conj-fallback-405
  [routes valid-method-keys-str]
  (conj-fallback-match routes
    (fn [_ _]
      {:status 405
       :headers {"Allow"        valid-method-keys-str
                 "Content-Type" "text/plain"}
       :body (str "405 Method not supported. Supported methods are: " valid-method-keys-str)})))


(defn make-uri-matcher
  "Given a route spec map containing :uri key with URI-pattern as value, return a matcher fn to match the URI."
  [{:keys [uri]
    :as spec}]
  (when-not (string? uri)
    (i/expected ":uri key to exist with a string value" spec))
  (let [uri-template (i/parse-uri-template i/default-separator uri)]
    (fn [request]
      (when-let [params (Util/matchURI ^String (:uri request) uri-template)]
        {:params params}))))


(defn make-method-matcher
  "Given a route spec map containing :method key with method keyword (or keyword set) as value, return a matcher fn to
  match the method."
  [{:keys [method]
    :as spec}]
  (when-not (or (keyword? method)
              (and (set? method)
                (every? keyword? method)))
    (i/expected ":method key to exist with a keyword or keyword-set value" spec))
  (cond
    (keyword? method) (fn [request]
                        (when (= (:request-method request) method)
                          {}))
    (set? method)     (fn [request]
                        (when (method (:request-method request))
                          {}))))


(defn make-routes
  "Given a collection of raw route-spec maps, populate the routes with a suitable matcher.
  See: make-uri-matcher, make-method-matcher"
  [make-matcher routes]
  (when-not (coll? routes)
    (i/expected "routes to be a collection" routes))
  (doseq [spec routes]
    (when-not (map? spec)
      (i/expected "route spec to be a map" spec)))
  (mapv (fn [{:keys [matcher]
              :as spec}]
          (if matcher
            spec
            (assoc spec :matcher (make-matcher spec))))
    routes))
