# calfpath

A Clojure library for _à la carte_ (orthogonal) ring request matching. 

(_Calf path_ is a synonym for [Desire path](http://en.wikipedia.org/wiki/Desire_path). [The Calf-Path](http://www.poets.org/poetsorg/poem/calf-path) is a poem by _Sam Walter Foss_.)


## Usage

Leiningen dependency: `[calfpath "0.2.0"]`

Require namespace:
```clojure
(require '[calfpath.core :refer
                         [->uri ->method ->get ->head ->options ->put ->post ->delete
                          make-uri-handler]])
```

### Examples

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
                                                 :body ("Updated CompanyID: %s, Dept ID: %s" cid did)}))
    "/this/is/a/static/route"  (fn [request _]
                                 (->put request {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "Lorem Ipsum"}))
    (fn [_] {:status 400
             :headers {"Content-Type" "text/plain"}
             :body "400 Bad request. URI does not match any available uri-template."})))
```

## Development

You need JDK 1.7 or higher during development.

Running tests: `lein with-profile c17 test`

Running performance benchmarks: `lein with-profile c17,perf test`


## License

Copyright © 2015 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
