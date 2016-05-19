;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route
  (:require
    [clojure.string :as string]
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
                       :matchex arity-1 fn  ; optional (enabled by default)
                       :nested  route-map   ; either of :nested and :handler keys must be present
                       :handler arity-2 fn}
  1. A matcher is arity-1 fn (accepts request as argument) that returns {:request request :params route-param-map}
     on successful match, nil otherwise. A matcher may update the request (on successful match) before passing it on.
  2. A matchex is arity-1 fn (accepts request symbol as argument) that returns expression to eval instead of calling
     matcher. The matchex is used only when :matcher is also present. Expr should return a value similar to matcher.
  3. A matchex is for optimization only, which may be disabled by setting a false or nil value for the :matchex key.
  4. Handler is arity-2 function (accepts request and match-param-map as arguments)."
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
                   ;; eval-forms can only access information via root-level vars
                   ;; so we use the dynamic var *routes* here
                   (into `[~routes-sym ~'calfpath.route/*routes*]))
        all-exps (->> (range n)
                   reverse
                   (reduce (fn
                             ([expr]
                               expr)
                             ([expr idx]
                               (let [matcher-sym (get matcher-syms idx)
                                     matcher-exp (if-let [matchex (:matchex (get routes idx))]
                                                   (matchex request-sym)
                                                   `(~matcher-sym ~request-sym))
                                     handler-sym (get handler-syms idx)]
                                 `(if-let [match# ~matcher-exp]
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


;; ----- fallback route match -----


(defn conj-fallback-match
  "Given a route vector append a matcher that always matches with a corresponding specified handler."
  [routes handler]
  (conj routes {:matcher (fn [_] {})
                :handler handler}))


(defn conj-fallback-400
  ([routes {:keys [show-uris? uri-finder uri-prefix] :as opts}]
    (when (and show-uris? (not uri-finder))
      (i/expected ":show-uris? key to be accompanied by :uri-finder key" opts))
    (let [uri-list-str (when show-uris?
                         (->> (filter uri-finder routes)
                           (map uri-finder)
                           (map (partial str uri-prefix))
                           (cons "Available URI templates:")
                           (string/join \newline)
                           (str "\n\n")))
          response-400 {:status 400
                        :headers {"Content-Type" "text/plain"}
                        :body (str "400 Bad request. URI does not match any available uri-template." uri-list-str)}]
      (conj-fallback-match routes
        (fn [_ _] response-400))))
  ([routes]
    (conj-fallback-400 routes {})))


(defn conj-fallback-405
  [routes {:keys [allowed-methods method-finder] :as opts}]
  (when (not (or allowed-methods method-finder))
    (i/expected "either :allowed-methods or :method-finder key to be present" opts))
  (let [as-str (fn [x] (if (instance? clojure.lang.Named x)
                         (name x)
                         (str x)))
        methods-list (or allowed-methods
                       (->> (filter method-finder routes)
                         (map method-finder)
                         flatten
                         (map as-str)
                         (map string/upper-case)
                         distinct
                         (string/join ", ")))
        response-405 {:status 405
                      :headers {"Allow"        methods-list
                                "Content-Type" "text/plain"}
                      :body (str "405 Method not supported. Allowed methods are: " methods-list)}]
    (conj-fallback-match routes
      (fn [_ _] response-405))))


;; ----- update bulk routes -----


(defn update-routes
  "Given a bunch of routes, update every route-collection (recursively) with f."
  [routes f & args]
  (when-not (coll? routes)
    (i/expected "routes to be a collection" routes))
  (doseq [spec routes]
    (when-not (map? spec)
      (i/expected "route spec to be a map" spec)))
  (as-> routes $
    (mapv (fn [spec]
            (if (contains? spec :nested)
              (apply update-in spec [:nested] update-routes f args)
              spec))
      $)
    (apply f $ args)))


(defn update-fallback-400
  "Update routes by appending a fallback HTTP-400 route only when all routes have :uri key."
  ([routes uri-finder opts]
    (if (every? uri-finder routes)
      (conj-fallback-400 routes (assoc opts :uri-finder uri-finder))
      routes))
  ([routes]
    (update-fallback-400 {})))


(defn update-fallback-405
  "Update routes by appending a fallback HTTP-405 route when all routes have :method key."
  ([routes method-finder opts]
    (if (every? method-finder routes)
      (conj-fallback-405 routes (assoc opts :method-finder method-finder))
      routes))
  ([routes method-finder]
    (update-fallback-405 routes method-finder {})))


;; ----- update each route -----


(defn update-each-route
  "Given a bunch of routes, update every route (recursively) with f."
  [routes f & args]
  (when-not (coll? routes)
    (i/expected "routes to be a collection" routes))
  (doseq [spec routes]
    (when-not (map? spec)
      (i/expected "route spec to be a map" spec)))
  (mapv (fn [spec]
          (let [spec (if (contains? spec :nested)
                       (apply update-in spec [:nested] update-each-route f args)
                       spec)]
            (apply f spec args)))
    routes))


(defn make-ensurer
  "Given a key and factory fn (accepts route and other args, returns new route), create a route updater fn that applies
  f to the route only when it does not contain the key."
  [k f]
  (fn [spec & args]
    (when-not (map? spec)
      (i/expected "route spec to be a map" spec))
    (if (contains? spec k)
      spec
      (apply f spec args))))


(defn make-updater
  "Given a key and updater fn (accepts route and other args, returns new route), create a route updater fn that applies
  f to the route only when it contains the key."
  [k f]
  (fn [spec & args]
    (when-not (map? spec)
      (i/expected "route spec to be a map" spec))
    (if (contains? spec k)
      (apply f spec args)
      spec)))


;; ----- ensure matcher in routes -----


(def make-uri-matcher
  "Given a route spec map containing :uri key with URI-pattern as value, return a matcher fn to match the URI. If the
  route spec does not contain :uri key then it is left intact."
  (make-ensurer :matcher
    (fn [spec uri-finder]
      (when-not (map? spec)
        (i/expected "route spec to be a map" spec))
      (if-let [uri-pattern (uri-finder spec)]  ; assoc matcher only if URI matcher is intended
        (do
          (when-not (string? uri-pattern)
            (i/expected "URI pattern to be retrievable as a string value" spec))
          (let [uri-template (i/parse-uri-template i/default-separator uri-pattern)
                ensure-matchex (make-ensurer :matchex
                                 (fn [spec x]
                                   (assoc spec :matchex x)))]
            (-> spec
              (assoc :matcher (fn [request]
                                (when-let [params (Util/matchURI ^String (:uri request) uri-template)]
                                  {:params params})))
              (ensure-matchex (fn [request]
                                `(when-let [params# (Util/matchURI ^String (:uri ~request) ~uri-template)]
                                   {:params params#}))))))
        spec))))


(def make-method-matcher
  "Given a route spec map containing :method key with method keyword (or keyword set) as value, return a matcher fn to
  match the method. If the route spec does not contain :method key then it is left intact."
  (make-ensurer :matcher
    (fn [spec method-finder]
      (when-not (map? spec)
        (i/expected "route spec to be a map" spec))
      (if-let [method (method-finder spec)]  ; assoc matcher only if method matcher is intended
        (let [ensure-matchex (make-ensurer :matchex (fn [spec x]
                                                      (assoc spec :matchex x)))]
          (when-not (or (keyword? method)
                      (and (set? method)
                        (every? keyword? method)))
            (i/expected "HTTP method key to be retrievable as a keyword or keyword-set value" spec))
          (cond
            (keyword? method) (-> spec
                                (assoc :matcher (fn [request]
                                                  (when (= (:request-method request) method)
                                                    {})))
                                (ensure-matchex (fn [request]
                                                  `(when (= (:request-method ~request) ~method)
                                                     {}))))
            (set? method)     (-> spec
                                (assoc :matcher (fn [request]
                                                  (when (method (:request-method request))
                                                    {})))
                                (ensure-matchex (fn [request]
                                                  `(when (~method (:request-method ~request))
                                                     {}))))))
        spec))))


;; ----- manipulate nested routes -----


(defn cons-matcher
  "Given a bunch of routes, nest all of them under a specified matcher."
  [matcher routes]
  [{:matcher matcher :nested routes}])


(defn cons-uri-prefix-matcher
  "Given a bunch of routes, nest all of them under a URI-prefix matcher."
  [^String prefix routes {:keys [strip? replace? original-key]
                          :as options}]
  (cons-matcher
    (let [plen (.length prefix)]
      (cond
        (and strip? replace?)       (fn [request]
                                      (let [^String uri (:uri request)]
                                        (when (and (.startsWith uri prefix)
                                                (> (.length uri) plen))
                                          {:request (assoc request
                                                      :uri         (.substring uri plen)
                                                      original-key uri)})))
        (and strip? (not replace?)) (fn [request]
                                      (let [^String uri (:uri request)]
                                        (when (and (.startsWith uri prefix)
                                                (> (.length uri) plen))
                                          {:request (assoc request
                                                      :uri (.substring uri plen))})))
        (and (not strip?) replace?) (fn [request]
                                      (let [^String uri (:uri request)]
                                        (when (and (.startsWith uri prefix)
                                                (> (.length uri) plen))
                                          {:request (assoc request
                                                      original-key uri)})))
        :otherwise                  (fn [request]
                                      (let [^String uri (:uri request)]
                                        (when (and (.startsWith uri prefix)
                                                (> (.length uri) plen))
                                          {})))))
    routes))
