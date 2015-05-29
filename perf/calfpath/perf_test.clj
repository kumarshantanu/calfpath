(ns calfpath.perf-test
  (:require
    [clojure.test :refer [deftest testing use-fixtures]]
    [compojure.core :refer [defroutes rfn routes context GET POST PUT ANY]]
    [calfpath.core  :refer [match-route match-method]]
    [calfpath.test-harness :as h]))


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
  (context "/company/:cid/dept/:did"  [cid did]
    (PUT "/" request {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body "3"})
    (ANY "/" request {:status 405
                      :headers {"Allow" "PUT"
                                "Content-Type" "text/plain"}
                      :body "405 Method not supported. Supported methods are: PUT"}))
  (context "/this/is/a/static/route"  []
    (PUT "/" request {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body "4"})
    (ANY "/" request {:status 405
                      :headers {"Allow" "PUT"
                                "Content-Type" "text/plain"}
                      :body "405 Method not supported. Supported methods are: PUT"}))
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
                                                 :body "2"})
    "/company/:cid/dept/:did/" [cid did] (match-method request
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "3"})
    "/this/is/a/static/route"  []        (match-method request
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "4"})))


(use-fixtures :once (h/make-bench-test-wrapper "Compojure" "CalfPath" "bench.png"))


(deftest test-no-match
  (testing "no route match"
    (let [request {:request-method :get
                   :uri "/hello/joe/"}]
      (h/compare-perf (handler-compojure request) (handler-calfpath request)))))


(deftest test-match
  (testing "no route match"
    (let [request {:request-method :put
                   :uri "/this/is/a/static/route"}]
      (h/compare-perf (handler-compojure request) (handler-calfpath request)))))