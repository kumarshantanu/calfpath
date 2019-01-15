# calfpath

[![Build Status](https://travis-ci.org/kumarshantanu/calfpath.svg)](https://travis-ci.org/kumarshantanu/calfpath)

A Clojure library for _à la carte_ (orthogonal) [Ring](https://github.com/ring-clojure/ring) request matching and routing.

(_Calf path_ is a synonym for [Desire path](http://en.wikipedia.org/wiki/Desire_path).
[The Calf-Path](http://www.poets.org/poetsorg/poem/calf-path) is a poem by _Sam Walter Foss_.)


## Rationale

- Ring has no built-in routing mechanism; Calfpath delivers this essential feature.
- Orthogonality - match URI patterns, HTTP methods or anything in a Ring request.
- Calfpath is fast (benchmarks included) - there is no cost to what you do not use.
- API is available as both dispatch macros and extensible, data-driven routes.


## Usage

Leiningen dependency: `[calfpath "0.7.2"]` (requires Clojure 1.7 or later)

Require namespace:
```clojure
(require '[calfpath.core  :refer [->uri ->method ->get ->head ->options ->patch ->put ->post ->delete]])
(require '[calfpath.route :as r])
```


### Direct HTTP URI/method dispatch

When you need to dispatch on URI pattern with convenient API:
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


### Data-driven Routes abstraction

Calfpath supports data-driven _routes_ where every route is a map of certain keys. Routes are easy to
extend and re-purpose. An example low-level route looks like the following (where route-handler is the
same as a Ring handler function):

```clojure
{:matcher uri-matcher :nested [{:matcher get-matcher :handler handler1}
                               {:matcher post-matcher :handler handler2}]}
```

Every route has two required keys - `:matcher` and `:handler`/`:nested` as described below:

| Key        | Required? | Description |
|------------|-----------|-------------|
| `:matcher` |    Yes    | `(fn [request]) -> request?` returns request on success and `nil` on failure |
| `:nested`  |   Either  | Routes vector - nested match is attempted on this if matcher was successful |
| `:handler` |   Either  | `(fn [request]) -> response` returns Ring response map, like a Ring handler |

Calfpath comes with utilities to create common matchers, e.g. URI, HTTP method, fallback etc.

#### Quickstart

See the route examples below:

```clojure

;; a route-handler is arity-1 (or arity-3 for async) fn, like a ring-handler
(defn list-user-jobs
  [{:keys [user-id] :as request}]
  ...)

(defn app-routes
  "Return a vector of route specs."
  []
  [;; first route has a partial URI match,implied by a trailing '*'
   {:uri "/users/:user-id*" :nested [{:uri "/jobs/"        :nested [{:method :get  :handler list-user-jobs}
                                                                    {:method :post :handler assign-job}]}
                                     {:uri "/permissions/" :method :get :handler permissions-hanler}]}
   {:uri "/orders/:order-id/confirm/" :method :post :handler confirm-order}        ; :uri is lifted over :method
   {:uri "/health/"  :handler health-status}
   {:uri "/static/*" :handler (-> (fn [_] {:status 400 :body "No such file"})      ; static files serving example
                                ;; the following require Ring dependency in your project
                                (ring.middleware.resource/wrap-resource "public")  ; render files from classpath
                                (ring.middleware.file/wrap-file "/var/www/public") ; render files from filesystem
                                (ring.middleware.content-type/wrap-content-type)
                                (ring.middleware.not-modified/wrap-not-modified))}])

;; create a Ring handler from given routes
(def ring-handler
  (-> (app-routes)   ; return routes vector
    r/compile-routes ; turn every map into a route by populating matchers in them
    r/make-dispatcher))
```

#### Notes on routes

- The `:matcher` key must be present in a route spec for dispatch.
  - In practice, other keys (e.g. `:uri`, `:method` etc.) add the `:matcher` key
- Either `:handler` or `:nested` key must be present in a route spec.
- A successful match may return an updated request, or the same request, or `nil`


## Development

You need JDK 1.7 or higher during development.

Running tests:

```shell
$ lein do clean, test
$ lein with-profile c07 test
```

Running performance benchmarks:

```shell
$ lein do clean, perf-test
$ lein with-profile c07,perf test  # on specified Clojure version
```


## License

Copyright © 2015-2019 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
