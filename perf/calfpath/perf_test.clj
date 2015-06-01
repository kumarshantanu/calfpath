(ns calfpath.perf-test
  (:require
    [clojure.test :refer [deftest testing use-fixtures]]
    [compojure.core :refer [defroutes rfn routes context GET POST PUT ANY]]
    [calfpath.core  :refer [->uri ->method]]
    [citius.core   :as c]))


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
  (->uri request
    "/user/:id/profile/:type/" [id type] (->method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "1"})
    "/user/:id/permissions/"   [id]      (->method request
                                           :get {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "2"})
    "/company/:cid/dept/:did/" [cid did] (->method request
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "3"})
    "/this/is/a/static/route"  []        (->method request
                                           :put {:status 200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body "4"})))


(use-fixtures :once (c/make-bench-wrapper ["Compojure" "CalfPath"]
                      {:chart-title "Compojure vs CalfPath"
                       :chart-filename (format "bench-clj-%s.png" c/clojure-version-str)}))


(deftest test-no-match
  (testing "no URI match"
    (let [request {:request-method :get
                   :uri "/hello/joe/"}]
      (c/compare-perf "no URI match" (handler-compojure request) (handler-calfpath request))))
  (testing "no method match"
    (let [request {:request-method :put
                   :uri "/user/1234/profile/compact/"}]
      (c/compare-perf "no method match" (handler-compojure request) (handler-calfpath request)))))


(deftest test-match
  (testing "static route match"
    (let [request {:request-method :put
                   :uri "/this/is/a/static/route"}]
      (c/compare-perf "static route match" (handler-compojure request) (handler-calfpath request))))
  (testing "pattern route match"
    (let [request {:request-method :get
                   :uri "/user/1234/profile/compact/"}]
      (c/compare-perf "pattern route match" (handler-compojure request) (handler-calfpath request)))))