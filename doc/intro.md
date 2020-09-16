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
corresponding handler code.

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


### Applying middleware

TODO


### From route to request (bi-directional routing)

TODO
