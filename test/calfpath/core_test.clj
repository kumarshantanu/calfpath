(ns calfpath.core-test
  (:require [clojure.test :refer :all]
            [calfpath.core :refer :all]))


(deftest test-match-route
  (testing "No clause"
    (let [request {:uri "/user/1234/profile/compact/"}]
      (is (= 400
            (:status (match-route request))))))
  (testing "One clause (no match)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 400
            (:status (match-route request
                       "/user/:id/profile/:type/" [id type] (do {:status 200
                                                                 :body (format "ID: %s, Type: %s" id type)})))))))
  (testing "One clause (with match)"
    (let [request {:uri "/user/1234/profile/compact/"}]
      (is (= "ID: 1234, Type: compact"
            (:body (match-route request
                     "/user/:id/profile/:type/" [id type] {:status 200
                                                           :body (format "ID: %s, Type: %s" id type)}))))))
  (testing "Two clauses (no match)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 400
            (:status (match-route request
                       "/user/:id/profile/:type/" [id type] {:status 200
                                                             :body (format "ID: %s, Type: %s" id type)}
                       "/user/:id/permissions/"   [id]      {:status 200
                                                             :body (format "ID: %s" id)}))))))
  (testing "Two clauses (no match, custom default)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 404
            (:status (match-route request
                       "/user/:id/profile/:type/" [id type] {:status 200
                                                             :body (format "ID: %s, Type: %s" id type)}
                       "/user/:id/permissions/"   [id]      {:status 200
                                                             :body (format "ID: %s" id)}
                       {:status 404
                        :body "Not found"}))))))
  (testing "Two clause (with match)"
    (let [request {:uri "/user/1234/permissions/"}]
      (is (= "ID: 1234"
            (:body (match-route request
                     "/user/:id/profile/:type/" [id type] {:status 200
                                                           :body (format "ID: %s, Type: %s" id type)}
                     "/user/:id/permissions/"   [id]      {:status 200
                                                           :body (format "ID: %s" id)})))))))


(deftest test-match-method
  (testing "No clause"
    (let [request {:request-method :get}]
      (is (= 405
            (:status (match-method request))))))
  (testing "One clause (no match)"
    (let [request {:request-method :get}]
      (is (= 405
            (:status (match-method request
                       :put {:status 200
                             :body   "Updated"}))))))
  (testing "One clause (with match)"
    (let [request {:request-method :get}]
      (is (= 200
            (:status (match-method request
                       :get {:status 200
                             :body   "Data"}))))))
  (testing "Two clauses (no match)"
    (let [request {:request-method :delete}]
      (is (= 405
            (:status (match-method request
                       :get {:status 200
                             :body   "Data"}
                       :put {:status 200
                             :body   "Updated"}))))))
  (testing "Two clauses (no match, custom default)"
    (let [request {:request-method :delete}]
      (is (= 404
            (:status (match-method request
                       :get {:status 200
                             :body   "Data"}
                       :put {:status 200
                             :body   "Updated"}
                       {:status 404
                        :body   "Not found"}))))))
  (testing "Two clauses (with match)"
    (let [request {:request-method :put}]
      (is (= "Updated"
            (:body (match-method request
                     :get {:status 200
                           :body   "Data"}
                     :put {:status 200
                           :body   "Updated"})))))))


(defn composite
  [request]
  (match-route request
    "/user/:id/profile/:type/" [id type] (match-method request
                                           :get {:status 200
                                                 :body (format "Compact profile for ID: %s, Type: %s" id type)}
                                           :put {:status 200
                                                 :body (format "Updated ID: %s, Type: %s" id type)})
    "/user/:id/permissions/"   [id]      (match-method request
                                           :post {:status 201
                                                  :body "Created new permission"})))


(deftest test-composite
  (testing "No route match"
    (is (= 400
          (:status (composite {:request-method :get
                               :uri "/hello/1234/"})))))
  (testing "Matching route and method"
    (is (= "Compact profile for ID: 1234, Type: compact"
          (:body (composite {:request-method :get
                             :uri "/user/1234/profile/compact/"})))))
  (testing "Matching route, but no matching method"
    (is (= 405
          (:status (composite {:request-method :delete
                               :uri "/user/1234/profile/compact/"}))))))