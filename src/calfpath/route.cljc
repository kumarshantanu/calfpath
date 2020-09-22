;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route
  #?(:cljs (:require-macros calfpath.route))
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [calfpath.internal :as i])
  #?(:clj (:import [clojure.lang Associative])))


(defn dispatch
  "Given a vector of routes, recursively walk the routes evaluating the specified Ring request with each matcher.
  Invoke corresponding handler on successful match.
  Synopsis:
  0. A route is a map {:matcher `(fn [request]) -> request?` ; :matcher is a required key
                       :nested  vector of child routes       ; either :handler or :nested key must be present
                       :handler Ring handler for route}
  1. A matcher is (fn [request]) that returns a potentially-updated request on successful match, nil otherwise.
  2. Handler is a Ring handler (fn [request] [request respond raise]) that responds to a Ring request.

  See: [[compile-routes]], [[make-dispatcher]] (Clojure/JVM only)"
  ([routes request f] ;; (f handler updated-request)
    (loop [routes (seq routes)]
      (when routes
        (let [current-route (first routes)]
          (if-some [matcher (get current-route :matcher)]
            (if-some [updated-request (matcher request)]
              (cond
                (contains? current-route :handler) (f (:handler current-route) updated-request)
                (contains? current-route :nested)  (dispatch (:nested current-route) updated-request)
                :otherwise                         (i/expected ":handler or :nested key to be present in route"
                                                     current-route))
              (recur (next routes)))
            (i/expected ":matcher key to be present in route" current-route))))))
  ([routes request]
    (dispatch routes request i/invoke))
  ([routes request respond raise]
    (dispatch routes request (fn [handler updated-request]
                               (handler updated-request respond raise)))))


;; Below is the outline of a loop-unrolled optimized version that returns a function that recursively matches routes
;; against the request:
;
;(let [m0 (:matcher (get routes 0))
;      h0 (or (:handler (get routes 0))
;           (make-handler (:nested (get routes 0))))
;      m1 ; similar to m0, for element 1
;      h1 ; similar to h0, for element 1
;      ....]
;  (fn dispatcher
;    [original-request] ; or [original-request respond raise] for async handlers
;    (if-some [updated-request (m0 original-request)]
;      (h0 updated-request) ; or (h0 updated-request respond raise) for async handlers
;      (if-some [....] ; similar to m0, for element 1
;        .... ; similar to h0, for element 1
;        ....))))
;
;; The `make-dispatcher` function analyzes the routes collection and prepares a loop-unrolled form that is
;; evaluated at the end to create the Ring handler function.


(def ^:dynamic
  *routes* :foo)


#?(:clj (defn make-dispatcher
          "Given a collection of routes return a Ring handler function that matches specified request and invokes
  corresponding route handler on successful match, returns nil otherwise.
  Synopsis:
  0. A route is a map {:matcher `(fn [request]) -> request?`     ; :matcher is a required key
                       :matchex `(fn [request-sym]) -> matcher`  ; optional (enabled by default)
                       :nested  routes-vector                    ; either :handler or :nested key must be present
                       :handler Ring handler}
  1. A matcher is (fn [request]) that returns a potentially-updated request on successful match, nil otherwise.
  2. A matchex is (fn [request-sym]) that returns expression to eval instead of calling matcher. The matchex is used
     only when :matcher is also present. Expr should return a value similar to matcher.
  3. A matchex is for optimization only, which may be disabled by setting a false or nil value for the :matchex key.
  4. Handler is a Ring handler (fn [request] [request respond raise]) that responds to a Ring request.

  See: [[compile-routes]], [[dispatch]]"
          ([routes]
            (make-dispatcher routes {}))
          ([routes {:keys [uri-key method-key]
                    :or {uri-key :uri
                         method-key :method}
                    :as options}]
            (let [routes (->> routes
                           (map (fn [spec]
                                  (when-not (:matcher spec)
                                    (i/expected ":matcher key to be present" spec))
                                  (condp #(contains? %2 %1) spec
                                    :handler spec
                                    :nested  (assoc spec :handler (make-dispatcher (:nested spec)))
                                    (i/expected ":nested or :handler key to be present in route"
                                      spec))))
                           vec)
                  routes-sym   (gensym "routes-")
                  dispatch-sym (gensym "dispatch-")
                  request-sym  (gensym "request-")
                  invoke-sym   (gensym "invoke-handler-")
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
                  options  {:uri-key uri-key :method-key method-key}
                  all-exps (i/make-dispatcher-expr routes matcher-syms handler-syms request-sym invoke-sym options)
                  fn-form  `(let [~@bindings]
                              (fn ~dispatch-sym
                                ([~request-sym ~invoke-sym]
                                 ~all-exps)
                                ([~request-sym]
                                 (~dispatch-sym ~request-sym i/invoke))
                                ([~request-sym respond# raise#]
                                 (~dispatch-sym ~request-sym (fn [handler# updated-request#]
                                                               (handler# updated-request# respond# raise#))))))]
              (binding [*routes* routes]
                (eval fn-form))))))


;; ----- fallback route match -----


(defn conj-fallback-match
  "Given a route vector append a matcher that always matches with a corresponding specified handler."
  [routes handler]
  (conj routes {:matcher identity
                :matchex identity
                :handler handler}))


(defn conj-fallback-400
  "Given a route vector append a matcher that always matches, and a handler that returns HTTP 400 response."
  ([routes {:keys [show-uris? uri-finder uri-prefix] :as opts}]
    (when (and show-uris? (not uri-finder))
      (i/expected ":show-uris? key to be accompanied by :uri-finder key" opts))
    (let [uri-list-str (when show-uris?
                         (->> (filter uri-finder routes)
                           (map uri-finder)
                           (map (partial str uri-prefix))
                           sort
                           (cons "Available URI templates:")
                           (string/join \newline)
                           (str "\n\n")))
          response-400 {:status 400
                        :headers {"Content-Type" "text/plain"}
                        :body (str "400 Bad request. URI does not match any available uri-template." uri-list-str)}]
      (conj-fallback-match routes
        (fn ([_] response-400)
          ([_ respond _] (respond response-400))))))
  ([routes]
    (conj-fallback-400 routes {})))


(defn conj-fallback-405
  "Given a route vector append a matcher that always matches, and a handler that returns HTTP 405 response."
  [routes {:keys [allowed-methods method-finder] :as opts}]
  (when (not (or allowed-methods method-finder))
    (i/expected "either :allowed-methods or :method-finder key to be present" opts))
  (let [as-str (fn [x] (if #?(:cljs (cljs.core/implements? INamed x)
                               :clj (instance? clojure.lang.Named x))
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
      (fn ([_] response-405)
        ([_ respond _] (respond response-405))))))


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
              (apply update spec :nested update-routes f args)
              spec))
      $)
    (apply f $ args)))


(defn update-fallback-400
  "Update routes by appending a fallback HTTP-400 route only when all routes have :uri key."
  ([routes uri-finder opts]
    (if (some uri-finder routes)
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
                       (apply update spec :nested update-each-route f args)
                       spec)]
            (apply f spec args)))
    routes))


(defn prewalk-routes
  "Given a bunch of routes, update every route (recursively) with f, which receives parent route as second arg."
  [routes parent-route f & args]
  (when-not (coll? routes)
    (i/expected "routes to be a collection" routes))
  (doseq [spec routes]
    (i/expected map? "route spec to be a map" spec))
  (mapv (fn [each-route]
          (let [walked-route (apply f each-route parent-route args)]
            (if (contains? walked-route :nested)
              (apply update walked-route :nested prewalk-routes walked-route f args)
              walked-route)))
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


(defn update-in-each-route
  "Given a bunch of routes, update every route (recursively) containing specified attribute with the given wrapper. The
  wrapper fn f is invoked with the old attribute value, and the returned value is updated into the route."
  [specs reference-key f]
  (->> #(update % reference-key f)
    (make-updater reference-key)
    (update-each-route specs)))


;; ----- ensure matcher in routes -----


(def ^{:arglists '([route-spec matchex])} ensure-matchex
  "Given a route spec not containing the :matchex key, assoc specified matchex into the spec. If the route spec already
  contains :matchex then leave it intact."
  (make-ensurer :matchex
    (fn [spec matchex]
      (assoc spec :matchex matchex))))


(def ^{:arglists '([route-spec uri-finder params-key])} make-uri-matcher
  "Given a route spec not containing the :matcher key and containing URI-pattern string as value (found by uri-finder),
  create a URI matcher and add it under the :matcher key. If the route spec already contains the :matcher key or if it
  does not contain URI-pattern then the route spec is left intact. When adding matcher also add matchex unless the
  :matchex key already exists."
  (make-ensurer :matcher
    (fn [spec uri-finder params-key]
      (i/expected map? "route spec to be a map" spec)
      (if-some [uri-pattern (uri-finder spec)]  ; assoc matcher only if URI matcher is intended
        (do
          (when-not (string? uri-pattern)
            (i/expected "URI pattern to be a string" spec))
          (let [params-sym    (-> (gensym "uri-params-")
                                (vary-meta assoc :tag "java.util.Map"))
                end-index-sym (gensym "end-index-")
                [uri-template partial?] (i/parse-uri-template i/default-separator uri-pattern)
                uri-str-token (first uri-template)
                uri-string?   (and (= 1 (count uri-template))
                                (string? uri-str-token))]
            (-> spec
              (assoc :matcher (if uri-string?
                                (if partial?
                                  (fn uri-matcher-token-partial [request]
                                    (let [end-index (int (i/get-uri-match-end-index request))
                                          new-index (i/partial-match-uri-string (:uri request)
                                                      end-index
                                                      uri-str-token)]
                                      (when (>= new-index i/FULL-MATCH-INDEX)
                                        (i/assoc-uri-match-end-index request new-index))))
                                  (fn uri-matcher-token-full [request]
                                    (let [end-index (int (i/get-uri-match-end-index request))
                                          new-index (i/full-match-uri-string (:uri request)
                                                      end-index
                                                      uri-str-token)]
                                      (when (= new-index i/FULL-MATCH-INDEX)
                                        (i/assoc-uri-match-end-index request new-index)))))
                                (fn uri-matcher [request]
                                  (when-some [^"[Ljava.lang.Object;"
                                              match-result (i/match-uri (:uri request)
                                                             (int (i/get-uri-match-end-index request))
                                                             uri-template partial?)]
                                    (let [params    (aget match-result 0)
                                          end-index (aget match-result 1)]
                                      (if (empty? params)
                                        (i/assoc-uri-match-end-index request end-index)
                                        (-> request
                                          (i/assoc-uri-match-end-index end-index)
                                          (update params-key i/conj-maps params))))))))
              (ensure-matchex (if uri-string?
                                (if partial?
                                  (fn uri-matcher-token-partial [request]
                                    `(let [end-index# (int (i/get-uri-match-end-index ~request))
                                           new-index# (i/partial-match-uri-string (:uri ~request)
                                                        end-index#
                                                        ~uri-str-token)]
                                      (when (>= new-index# i/FULL-MATCH-INDEX)
                                        (i/assoc-uri-match-end-index ~request new-index#))))
                                  (fn uri-matcher-token-full [request]
                                    `(let [end-index# (int (i/get-uri-match-end-index ~request))
                                           new-index# (i/full-match-uri-string (:uri ~request)
                                                        end-index#
                                                      ~uri-str-token)]
                                       (when (= new-index# i/FULL-MATCH-INDEX)
                                         (i/assoc-uri-match-end-index ~request new-index#)))))
                                (fn [request]
                                  `(when-some [^"[Ljava.lang.Object;"
                                               match-result# (i/match-uri (:uri ~request)
                                                               (int (i/get-uri-match-end-index ~request))
                                                               ~uri-template ~partial?)]
                                     (let [~params-sym    (aget match-result# 0)
                                           ~end-index-sym (aget match-result# 1)]
                                       (if (empty? ~params-sym)
                                         (i/assoc-uri-match-end-index ~request ~end-index-sym)
                                         (-> ~request
                                           (i/assoc-uri-match-end-index ~end-index-sym)
                                           (update ~params-key i/conj-maps ~params-sym)))))))))))
        spec))))


(def ^{:arglists '([route-spec method-finder])} make-method-matcher
  "Given a route spec not containing the :matcher key and containing HTTP-method keyword (or keyword set) as value
  (found by method-finder), create a method matcher and add it under the :matcher key. If the route spec already
  contains the :matcher key or if it does not contain HTTP-method keyword/set then the route spec is left intact. When
  adding matcher also add matchex unless the :matchex key already exists."
  (make-ensurer :matcher
    (fn [spec method-finder]
      (when-not (map? spec)
        (i/expected "route spec to be a map" spec))
      (if-some [method (method-finder spec)]  ; assoc matcher only if method matcher is intended
        (do
          (when-not (or (keyword? method)
                      (and (set? method)
                        (every? keyword? method)))
            (i/expected "HTTP method key to be retrievable as a keyword or keyword-set value" spec))
          (cond
            (keyword? method) (-> spec
                                ;; Clojure (not CLJS) keywords are interned; compare identity (faster), not equality
                                (assoc :matcher (fn method-matcher [request]
                                                  (when (#?(:cljs = :clj identical?)
                                                          (:request-method request) method)
                                                    request)))
                                (ensure-matchex (fn [request]
                                                  `(when (#?(:cljs = :clj identical?)
                                                           (:request-method ~request) ~method)
                                                     ~request))))
            (set? method)     (-> spec
                                (assoc :matcher (fn multiple-method-matcher [request]
                                                  (when (method (:request-method request))
                                                    request)))
                                (ensure-matchex (fn [request]
                                                  `(when (~method (:request-method ~request))
                                                     ~request))))))
        spec))))


;; ----- routes (bulk) middleware -----


(defn easy-routes
  "Allow easy, recursively applied, route definition as in the following examples:

  | Definition                         | Translated into                                       |
  |------------------------------------|-------------------------------------------------------|
  |`{\"/docs/:id/info\" [...]}`        |`{:uri \"/docs/:id/info\" :nested [...]}`              |
  |`{\"/docs/:id/info\" (fn [req])}`   |`{:uri \"/docs/:id/info\" :handler (fn [req])}`        |
  |`{:delete (fn [request])}`          |`{:method :delete :handler (fn [request])}`            |
  |`{[\"/docs/:id\" :get] (fn [req])}` |`{:uri \"/docs/:id\" :method :get :handler (fn [req])}`|"
  [routes uri-key method-key]
  (i/expected vector? "routes to be a vector of routes" routes)
  (let [add-assoc  (fn add-assoc*  ; additive assoc - do not replace existing keys
                     ([m k v]
                      (if (contains? m k) m (assoc m k v)))
                     ([m k v & pairs]
                      (let [base (add-assoc* m k v)]
                        (if (seq pairs)
                          (recur base (first pairs) (second pairs) (nnext pairs))
                          base))))
        replace-kv (fn [m k v] (-> m
                                 (add-assoc k v)
                                 (dissoc v)))
        add-key    (fn add-key* [m k]
                     (cond
                       (string? k)               (replace-kv m uri-key k)
                       (i/valid-method-keys k)   (replace-kv m method-key k)
                       (and (set? k)
                         (set/subset? k
                           i/valid-method-keys)) (replace-kv m method-key k)
                       (and (vector? k) (seq k)) (as-> m $
                                                   (reduce add-key* $ k)
                                                   (dissoc $ k))
                       :otherwise                m))
        add-target (fn add-target* [m target]
                     (cond
                       (vector? target) (as-> target $
                                          (easy-routes $ uri-key method-key)
                                          (add-assoc m :nested $))
                       (fn? target)     (add-assoc m :handler target)
                       :otherwise       (i/expected "target to be a routes vector or function" target)))]
    (mapv (fn [each-route]
            (reduce-kv (fn xform [m k v]
                         (let [m-with-k (add-key m k)]
                           (if (= m m-with-k)
                             m
                             (add-target m-with-k v))))
              each-route each-route))
      routes)))


(defn routes->wildcard-trie
  "Given a bunch of routes, segment them by prefix URI-tokens into a trie-like structure for faster match."
  ([routes {:keys [trie-threshold uri-key]
            :or {trie-threshold 1  ; agressive by default
                 uri-key :uri}
            :as options}]
    (i/triefy-all routes trie-threshold uri-key))
  ([routes]
    (routes->wildcard-trie routes {})))


;; ----- route middleware -----


(defn assoc-kv-middleware
  "Given a route spec, if the route contains the main key then ensure that it also has the associated key/value pairs."
  [spec main-key-finder assoc-map]
  (if (main-key-finder spec)
    (reduce-kv (fn [m k v] (if (contains? m k)
                             m
                             (assoc m k v)))
      spec assoc-map)
    spec))


(defn assoc-route-to-request-middleware
  "Given a route spec, decorate the handler such that the request has the spec under specified key (:route by default)
  at runtime."
  ([spec spec-key]
    (if (contains? spec :handler)
      (update spec :handler
        (fn middleware [f]
          (fn
            ([request] (f (assoc request spec-key spec)))
            ([request respond raise] (f (assoc request spec-key spec) respond raise)))))
      spec))
  ([spec]
    (assoc-route-to-request-middleware spec :route)))


(defn lift-key-middleware
  "Given a route spec, lift keys and one or more conflict keys, if the spec contains both any of the lift-keys and any
  of the conflict-keys then extract the lift keys such that all other attributes are moved into a nested spec."
  [spec lift-keys conflict-keys]
  (if (and
        (some #(contains? spec %) lift-keys)
        (some #(contains? spec %) conflict-keys))
    (-> spec
      (select-keys lift-keys)
      (assoc :nested [(apply dissoc spec lift-keys)]))
    spec))


(defn trailing-slash-middleware
  "Given a route spec, URI key and action (keyword :add or :remove) edit the URI to have or not have a trailing slash
  if the route has a URI pattern. Leave the route unchanged if it has no URI pattern."
  [spec uri-key action]
  (i/expected keyword? "URI key to be a keyword" uri-key)
  (i/expected #{:add :remove} "action to be :add or :remove" action)
  (if (contains? spec uri-key)
    (update spec uri-key (fn [uri]
                           (i/expected string? "URI to be a string" uri)
                           (if (string/ends-with? uri "*")  ; candidate for partial match?
                             uri                    ; do not change partial-match URIs
                             (let [trailing? (string/ends-with? uri "/")
                                   uri-length (count uri)]
                               (if (#?(:cljs = :clj identical?) action :add)
                                 (if trailing? uri (str uri "/"))           ; add trailing slash if missing
                                 (if (and trailing? (> uri-length 1))
                                   (subs uri 0 (unchecked-dec uri-length))  ; remove trailing slash if present
                                   uri))))))
    spec))


;; ----- helper fns -----


(defn compile-routes
  "Given a collection of route specs, supplement them with required entries and finally return a routes collection.

  ### Options

  | Kwarg           | Type  | Description                                                                            |
  |-----------------|-------|----------------------------------------------------------------------------------------|
  |`:easy?`         |boolean|allow easy defnition of routes that translate into regular routes                       |
  |`:trie?`         |boolean|optimize routes by automatically reorganizing routes as tries                           |
  |`:trie-threshold`|integer|similar routes more than this number will be grouped together                           |
  |`:uri?`          |boolean|true if URI templates should be converted to matchers                                   |
  |`:uri-key`       |non-nil|the key to be used to look up the URI template in a spec                                |
  |`:params-key`    |non-nil|the key to put URI params under in the request map                                      |
  |`:trailing-slash`|keyword|Trailing-slash action to perform on URIs - :add or :remove - nil (default) has no effect|
  |`:fallback-400?` |boolean|whether to add a fallback route to respond with HTTP status 400 for unmatched URIs      |
  |`:show-uris-400?`|boolean|whether to add URI templates in the HTTP 400 response (see :fallback-400?)              |
  |`:full-uri-key`  |non-nil|the key to be used to populate full-uri for reporting HTTP 400 (see :show-uris-400?)    |
  |`:uri-prefix-400`|string?|the URI prefix to use when showing URI templates in HTTP 400 (see :show-uris-400?)      |
  |`:method?`       |boolean|true if HTTP methods should be converted to matchers                                    |
  |`:method-key`    |non-nil|the key to be used to look up the method key/set in a spec                              |
  |`:fallback-405?` |boolean|whether to add a fallback route to respond with HTTP status 405 for unmatched methods   |
  |`:lift-uri?`     |boolean|whether lift URI attributes from mixed specs and move the rest into nested specs        |

  See: [[dispatch]], [[make-dispatcher]] (Clojure/JVM only), [[make-index]]"
  ([route-specs {:keys [easy?
                        trie?           trie-threshold
                        uri?            uri-key    fallback-400? show-uris-400? full-uri-key uri-prefix-400
                        params-key
                        method?         method-key fallback-405?
                        trailing-slash
                        lift-uri?
                        ring-handler? ring-handler-key]
                 :or {easy?           true
                      trie?           true   trie-threshold 1
                      uri?            true   uri-key     :uri     fallback-400? true  show-uris-400? true
                      params-key      :path-params
                      full-uri-key    :full-uri
                      method?         true   method-key  :method  fallback-405? true
                      lift-uri?       true
                      trailing-slash  false}
                 :as options}]
    (let [when-> (fn [specs test f & args] (if test
                                             (apply f specs args)
                                             specs))]
      (-> route-specs
        (when-> easy?                       easy-routes uri-key method-key)
        (when-> trie?                       update-routes routes->wildcard-trie {:trie-threshold trie-threshold
                                                                                 :uri-key uri-key})
        (when-> (and uri? method?
                  lift-uri?)                update-each-route lift-key-middleware [uri-key] [method-key])
        (when-> (and uri? trailing-slash)   update-each-route trailing-slash-middleware uri-key trailing-slash)
        (when-> (and method? fallback-405?) update-routes update-fallback-405 method-key)
        (when-> (and uri? fallback-400?
                  show-uris-400?
                  full-uri-key)             prewalk-routes nil (fn [route parent-route]
                                                                 (as-> (full-uri-key parent-route) $
                                                                   (i/strip-partial-marker $)
                                                                   (str $ (uri-key route))
                                                                   (assoc route full-uri-key $))))
        (when-> (and uri? fallback-400?)    update-routes update-fallback-400 (if (and show-uris-400? full-uri-key)
                                                                                full-uri-key
                                                                                uri-key) {:show-uris? show-uris-400?
                                                                                          :uri-prefix uri-prefix-400})
        (when-> method? update-each-route make-method-matcher method-key)
        (when-> uri?    update-each-route make-uri-matcher    uri-key params-key))))
  ([route-specs]
    (compile-routes route-specs {})))


;; ----- reverse routing (Ring request generation) -----


(defn make-index
  "Given a collection of routes, index them returning a map {:id route-template}.

  Options:

  | Kwarg       | Description                                      |
  |-------------|--------------------------------------------------|
  |`:index-key` |The index key in given routes, default `:id`      |
  |`:uri-key`   |The URI key in given routes, default `:uri`       |
  |`:method-key`|HTTP method key in given routes, default `:method`|

  See: [[compile-routes]], [[template->request]]"
  ([routes options]
    (:index-map (i/build-routes-index {:index-map  {}
                                       :uri-prefix ""
                                       :method     nil} routes options)))
  ([routes]
    (make-index routes {})))


(defn realize-uri
  "Given a vector of the form ['/users' :user-id '/profile/' :profile '/'] fill in the param values returning a URI.

  See: [[template->request]]"
  [uri-template {:keys [uri-params
                        uri-prefix
                        uri-suffix]
                 :as options}]
  (as-> uri-template $
    (reduce (fn [uri token]
              (if (string? token)
                #?(:cljs (str uri token)
                    :clj (.append ^StringBuilder uri ^String token))
                (if (contains? uri-params token)
                  #?(:cljs (str uri (get uri-params token))
                      :clj (.append ^StringBuilder uri (str (get uri-params token))))
                  (i/expected (str "URI param for key " token) uri-params))))
      #?(:cljs ""
         :clj (StringBuilder. (unchecked-multiply 5 (count uri-template))))
      $)
    (str uri-prefix $ uri-suffix)))


(defn template->request
  "Given a request template, realize the attributes to create a minimal Ring request.
  A request template may be found from a reverse index built using [[make-index]].

  Options:

  | Kwarg       | Description                                    |
  |-------------|------------------------------------------------|
  |`:uri-params`|map of URI param values, e.g. `{:lid 5 :rid 18}`|
  |`:uri-prefix`|URI prefix string, e.g. `\"https://myapp.com/\"`|
  |`:uri-suffix`|URI suffix string, e.g. `\"?q=beer&country=in\"`|

  See: [[make-index]]"
  ([request-template]
   (template->request request-template {}))
  ([request-template {:keys [uri-params
                             uri-prefix
                             uri-suffix]
                      :as options}]
   (-> request-template
     (update :uri realize-uri options)
     (update :request-method #(-> (if (set? %) (first %) %)
                                (or :get))))))
