(ns calfpath.perf-test
  (:require
    [compojure.core :refer [defroutes rfn routes context GET POST PUT ANY]]
    [calfpath.core  :refer [match-route match-method]]))


(defroutes handler-compojure
  (context "/user/:id/profile/:type/" [id type]
    (GET "/" request {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body "1"})
    (ANY "/" request {:status 405
                      :headers {"Allow" "GET"
                                "Content-Type" "text/plain"}
                      :body "405 Method not supported. Supported methods are: GET"}))
  (context "/user/:id/permissions"    [id]
    (GET "/" request {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body "2"})
    (ANY "/" request {:status 405
                      :headers {"Allow" "GET"
                                "Content-Type" "text/plain"}
                      :body "405 Method not supported. Supported methods are: GET"}))
  (rfn request {:status 400
                :headers {"Content-Type" "text/plain"}
                :body "400 Bad request. URI does not match any available route."}))


(defn handler-calfpath
  [request]
  (match-route request
    "/user/:id/profile/:type/" [id type] (match-method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "1"})
    "/user/:id/permissions/"   [id]      (match-method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "2"})))