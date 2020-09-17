# Introduction to calfpath

## Requiring namespace

```clojure
(:require
  [calfpath.core :refer [->uri ->method
                         ->get ->head ->options ->patch ->put ->post ->delete]]
  [calfpath.route :as r])
```


## Routing with convenience macros

Calfpath provides some convenience macros to match routes against request and invokes the
corresponding handler code. For data driven routing (recommended), see the next section.

```clojure
(defn handler
  [request]
  ;; ->uri is a macro that dispatches on URI pattern
  (->uri request
    "/user/:id*" [id]  (->uri request
                         "/profile/:type/" [type] (->method request
                                                    :get {:status 200
                                                          :headers {"Content-Type" "text/plain"}
                                                          :body (format "ID: %s, Type: %s" id type)}
                                                    :put {:status 200
                                                          :headers {"Content-Type" "text/plain"}
                                                          :body "Updated"})
                         "/permissions/"   []     (->method request
                                                    :get {:status 200
                                                          :headers {"Content-Type" "text/plain"}
                                                          :body (str "ID: " id)}
                                                    :put {:status 200
                                                          :headers {"Content-Type" "text/plain"}
                                                          :body (str "Updated ID: " id)}))
    "/company/:cid/dept/:did/" [cid did] (->put request
                                           {:status 200
                                            :headers {"Content-Type" "text/plain"}
                                            :body "Data"})
    "/this/is/a/static/route"  []        (->put request
                                           {:status 200
                                            :headers {"Content-Type" "text/plain"}
                                            :body "output"})))
```

In this example, we first check the URI followed by the nested method checks. The first URI group
uses a wildcard to match the common URI prefix `/user/:id`, then the remaining segment.


## Data driven routing

### Concept

Calfpath provides data driven routing via the API in `calfpath.route` namespace. It is based on the
concept of a route, which is a map of two required keys - a matcher (`:matcher`) that matches an
incoming request against the route, and a dispatch point (either `:nested` or `:handler`) for a
successful match. A route must contain the following keys:

- `:matcher` and `:nested`, or
- `:matcher` and `:handler`

Example:

```edn
[{:matcher m1 :nested [{:matcher m1-1 :handler h1-1}
                       {:matcher m1-2 :handler h1-2}]}
 {:matcher m1 :handler h1}]
```

Synopsis:

| Route key| Description |
|----------|-------------|
|`:matcher`|`(fn [request])` returning request on success, `nil` otherwise |
|`:nested` |vector of one or more sub routes                               |
|`:handler`|route handler, same arity as ring handler fn (regular or async)|


### Quickstart example

Calfpath provides matchers for common use cases, which we would see in an example below:

#### Route handler

A route handler is a function with same arity and semantics as a Ring handler.

```clojure
(defn list-user-jobs
  "Route handler for listing user jobs."
  [{:keys [user-id] :as request}]
  [:job-id 1
   :job-id 2])
```

#### Routes definition

A routes definition is a vector of route maps.

```clojure
(def easy-routes
  "Routes defined using a short, easy notation."
  [; partial URI match, implied by trailing '*'
   {"/users/:user-id*" [{["/jobs/"        :get] list-user-jobs}
                        {["/permissions/" :get] permissions-handler}]}
   {["/orders/:order-id/confirm/" :post] confirm-order}
   {"/health/" health-status}])
```

The easy routes definition above is translated as the longer notation below during route compilation:

```clojure
(def app-routes
  "Vector of application routes. To be processed by calfpath.route/compile-routes to generate matchers."
  [; partial URI match, implied by trailing '*'
   {:uri "/users/:user-id*" :nested [{:uri "/jobs/" :nested [{:method :get :handler list-user-jobs}]}
                                     {:uri "/permissions/" :method :get permissions-handler}]}
   {:uri "/orders/:order-id/confirm/" :method :post :handler confirm-order} ; :uri is lifted over :method
   {:uri "/health/" :handler health-status}])
```

Here, we do not specify any matcher directly but put relevant attributes to generate matchers
from, e.g. `:uri`, `:method` etc.


#### Serving static resources

Calfpath routes may be used to serve static resources using wrapped handlers.

```clojure
(def static-routes
  "Vector of static web resources"
  [{["/static/*" :get]
    (-> (fn [_] {:status 400 :body "No such file"})          ; fallback
          ;; the following requires [ring/ring-core "version"] dependency in your project
          (ring.middleware.resource/wrap-resource "public")  ; serve files from classpath
          (ring.middleware.file/wrap-file "/var/www/public") ; serve files from filesystem
          (ring.middleware.content-type/wrap-content-type)   ; detect and put content type
          (ring.middleware.not-modified/wrap-not-modified))}])
```


#### Making Ring handler

The routes vector must be turned into a Ring handler before it can be used.

```clojure
(def ring-handler
  (-> app-routes      ; the routes vector
    r/compile-routes  ; turn maps into routes by putting matchers in them
    r/make-dispatcher))
```


### Applying route middleware

Let us say you want to measure and log the total time taken by a route handler. How would you do that without
modifying every handler function or the routes vector? Using route middleware, as shown below:

```clojure
(defn tme-tracking-middleware
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          taken (fn [] (unchecked-subtract (System/currentTimeMillis) start))]
      (try
        (let [result (handler request)]
          (println "Time taken" (taken) "ms")
          result)
        (catch Exception e
          (println "Time taken" (taken) "ms, exception thrown:" e)
          (throw e))))))
```

This middleware needs to be applied to only the `:handler` value in all routes, which can be done as follows:

```clojure
(calfpath.route/update-in-each-route
  routes :handler time-tracking-middleware)
```

Should you need to inspect the entire route before updating anything, consider `calfpath.route/update-each-route`.
Note that you need to apply all middleware before making a Ring handler out of the routes.


### From route to request (bi-directional routing)

Bi-directional routing is when you can not only find a matching route for a given request, but you can generate one
given a route and the template parameters. To make routes bi-directiional you need to add unique identifier in every
route. Consider the following (easy notation) routes example:

```clojure
(def indexable-routes
  [{["/info/:token"             :get] identity :id :info}
   {["/album/:lid/artist/:rid/" :get] identity :id :album}
   {"/user/:id*" [{"/auth" identity :id :auth-user}
                  {"/permissions/"   [{:get    identity :id :read-perms}
                                      {:post   identity :id :save-perms}
                                      {:put    identity :id :update-perms}]}
                  {"/profile/:type/" [{:get    identity :id :read-profile}
                                      {:patch  identity :id :patch-profile}
                                      {:delete identity :id :remove-profile}]}
                  {:uri ""           identity}]}])
```

Every route (except the last one) having a handler function also has a unique ID that we can refer the route with.
Now we can build a reverse index:

```clojure
(calfpath.route/make-index indexable-routes)
```

It returns a reverse index looking like as follows:

```clojure
{:info           {:uri ["/info/" :token]                    :request-method :get}
 :album          {:uri ["/album/" :lid "/artist/" :rid "/"] :request-method :get}
 :auth-user      {:uri ["/user/" :id "/auth"]               :request-method :get}
 :read-perms     {:uri ["/user/" :id "/permissions/"]       :request-method :get}
 :save-perms     {:uri ["/user/" :id "/permissions/"]       :request-method :post}
 :update-perms   {:uri ["/user/" :id "/permissions/"]       :request-method :put}
 :read-profile   {:uri ["/user/" :id "/profile/" :type "/"] :request-method :get}
 :patch-profile  {:uri ["/user/" :id "/profile/" :type "/"] :request-method :patch}
 :remove-profile {:uri ["/user/" :id "/profile/" :type "/"] :request-method :delete}}
```

Now we can create a request based on any of the indexed routes:

```clojure
(-> (:album routes-index)
  (calfpath.route/template->request {:lid 10 :rid 20}))
```

It returns a request map looking like the one below:

```clojure
{:uri "/album/10/artist/20/"
 :request-method :get}
```

This structure matches the Ring request SPEC attributes.
