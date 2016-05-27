# calfpath

A Clojure library for _à la carte_ (orthogonal) ring request matching.

(_Calf path_ is a synonym for [Desire path](http://en.wikipedia.org/wiki/Desire_path). [The Calf-Path](http://www.poets.org/poetsorg/poem/calf-path) is a poem by _Sam Walter Foss_.)


## Usage

Leiningen dependency: `[calfpath "0.3.0"]`

Require namespace:
```clojure
(require '[calfpath.core :refer
                         [->uri ->method ->get ->head ->options ->patch ->put ->post ->delete
                          make-uri-handler]])
(require '[calfpath.route :as r])
```


### Direct HTTP URI/method dispatch

When you need to dispatch on URI pattern with convenient API:
```clojure
(defn handler
  [request]
  ;; ->uri is a macro that dispatches on URI pattern
  (->uri request
    "/user/:id/profile/:type/" [id type] (->method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body (format "ID: %s, Type: %s" id type)}
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "Updated"})
    "/user/:id/permissions/"   [id]      (->method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body (str "ID: " id)}
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body (str "Updated ID: " id)})
    "/company/:cid/dept/:did/" [cid did] (->put request
                                           {:status 200
                                            :headers {"Content-Type" "text/plain"}
                                            :body "Data"})
    "/this/is/a/static/route"  []        (->put request
                                           {:status 200
                                            :headers {"Content-Type" "text/plain"}
                                            :body "output"})))
```

When you need a function (for composition) that creates a Ring handler:
```clojure
(defn make-handler
  [app-config]
  (make-uri-handler
    "/user/:id/profile/:type/" (fn [request {:keys [id type]}]
                                 (->method request
                                   :get {:status 200
                                         :headers {"Content-Type" "text/plain"}
                                         :body (format "Data for ID: %s, Type: %s" id type)}
                                   :put {:status 200
                                         :headers {"Content-Type" "text/plain"}
                                         :body (format "Updated ID: %s, type: %s" id type)}))
    "/user/:id/permissions/"   (fn [request {:keys [id]}]
                                 (->method request
                                   :get {:status 200
                                         :headers {"Content-Type" "text/plain"}
                                         :body (str "Permissions for ID: " id)}
                                   :put {:status 200
                                         :headers {"Content-Type" "text/plain"}
                                         :body ("Updated permissions for ID: " id)}))
    "/company/:cid/dept/:did/" (fn [request {:keys [cid did]}]
                                 (->put request {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body ("Updated CompanyID: %s, Dept ID: %s"
                                                         cid did)}))
    "/this/is/a/static/route"  (fn [request _]
                                 (->put request {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "Lorem Ipsum"}))
    (fn [_] {:status 400
             :headers {"Content-Type" "text/plain"}
             :body "400 Bad request. URI does not match any available uri-template."})))
```

### Routes abstraction

In many cases we need to manipulate (i.e. add and extend) the dispatch criteria before handling the requests. This can
be addressed by the _routes_ abstraction. Routes are a vector of route specification maps. Every route has three
fundamental keys:

| Key        | Required? | Description |
|------------|-----------|-------------|
| `:matcher` |    Yes    | Arity-1 fn: accepts request map, returns `nil` on unsuccessful match and a non-`nil` map on\
successful match. Updated request is returned via `:request` key, route params are returned via `:params`. |
| `:nested`  |   Either  | Routes vector - match is attempted on this if matcher is successful. |
| `:handler` |   Either  | Arity-2 fn: accepts request map and params map, returns Ring response map. |

**Note:** Either of `:nested` and `:handler` keys must be present in a route spec.

See examples below:

```clojure
(defn make-routes
  "Return a vector of route specs."
  []
  [{:uri-template "/users/:user-id/jobs/"      :nested [{:method :get  :handler list-user-jobs}
                                                        {:method :post :handler assign-job}]}
   {:uri-template "/orders/:order-id/confirm/" :nested [{:method :post :handler confirm-order}]}
   {:uri-template "/health/" :handler health-status}])

(def ring-handler (-> (make-routes)
                    (r/update-routes r/update-fallback-405 :method)  ; add HTTP-405 fallbacks
                    (r/update-routes r/update-fallback-400 :uri-template {:show-uris? true})  ; add HTTP-400 fallbacks
                    (r/update-each-route r/make-method-matcher :method)  ; add method matchers under :matcher key
                    (r/update-each-route r/make-uri-matcher :uri-template)  ; add URI matchers under :matcher key
                    r/make-dispatcher))
```


## Development

You need JDK 1.7 or higher during development.

Running tests: `lein with-profile c17 test`

Running performance benchmarks: `lein with-profile c17,perf test`


## License

Copyright © 2015-2016 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
