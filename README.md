# calfpath

[![Build Status](https://travis-ci.org/kumarshantanu/calfpath.svg)](https://travis-ci.org/kumarshantanu/calfpath)
[![cljdoc badge](https://cljdoc.org/badge/calfpath/calfpath)](https://cljdoc.org/d/calfpath/calfpath)

A Clojure/Script library for _à la carte_ (orthogonal) [Ring](https://github.com/ring-clojure/ring) request matching,
routing and reverse-routing.

(_Calf path_ is a synonym for [Desire path](http://en.wikipedia.org/wiki/Desire_path).
[The Calf-Path](http://www.poets.org/poetsorg/poem/calf-path) is a poem by _Sam Walter Foss_.)


## Rationale

- Ring has no built-in routing mechanism; Calfpath delivers this essential feature.
- Orthogonality - match URI patterns, HTTP methods or anything in a Ring request.
- Calfpath is fast (benchmarks included) - there is no cost to what you do not use.
- Available as both dispatch macros and extensible, bi-directional, data-driven routes.


## Usage

Leiningen dependency: `[calfpath "0.8.0"]` (requires Clojure 1.8 or later, Java 7 or later)

Require namespace:
```clojure
(require '[calfpath.core  :refer [->uri ->method
                                  ->get ->head ->options ->patch ->put ->post ->delete]])
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

### Data-driven routes

Calfpath supports data-driven _routes_ where every route is a map of certain keys. Routes are easy to
extend and re-purpose. See an example below (where route-handler has the same arity as a Ring handler):

```clojure
;; a route-handler is arity-1 (or arity-3 for async) fn, like a ring-handler
(defn list-user-jobs
  [{{:keys [user-id] :path-params} :as request}]
  ...)

(defn app-routes
  "Return a vector of routes."
  []
  [;; first route has a partial URI match,implied by a trailing '*'
   {"/users/:user-id*" [{"/jobs/"        [{:get  list-user-jobs}
                                          {:post assign-job}]}
                        {["/permissions/" :get] permissions-hanler}]}
   {["/orders/:order-id/confirm/" :post] confirm-order}
   {"/health/"  health-status}
   {"/static/*" (-> (fn [_] {:status 400 :body "No such file"})      ; static files serving example
                  ;; the following requires [ring/ring-core "version"] dependency in your project
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


## Documentation

See [documentation page](doc/intro.md) for concepts, examples and more features.


## Development

Running tests:

```shell
$ lein do clean, test
$ lein with-profile c08 test
```

Running performance benchmarks:

```shell
$ lein do clean, perf-test
$ lein with-profile c08,perf test  # on specified Clojure version
```


## License

Copyright © 2015-2020 Shantanu Kumar

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
