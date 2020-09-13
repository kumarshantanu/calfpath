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

TODO


### Applying middleware

TODO


### From route to request (bi-directional routing)

TODO


### Routing for Websites

TODO
