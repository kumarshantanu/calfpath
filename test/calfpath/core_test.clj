(ns calfpath.core-test
  (:require [clojure.test :refer :all]
            [calfpath.core :as c]))


(deftest test-->uri
  (testing "No clause"
    (let [request {:uri "/user/1234/profile/compact/"}]
      (is (= 400
            (:status (c/->uri request))))))
  (testing "One clause (no match)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 400
            (:status (c/->uri request
                       "/user/:id/profile/:type/" [id type] (do {:status 200
                                                                 :body (format "ID: %s, Type: %s" id type)})))))))
  (testing "One clause (with match)"
    (let [request {:uri "/user/1234/profile/compact/"}]
      (is (= "ID: 1234, Type: compact"
            (:body (c/->uri request
                     "/user/:id/profile/:type/" [id type] {:status 200
                                                           :body (format "ID: %s, Type: %s" id type)}))))))
  (testing "Two clauses (no match)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 400
            (:status (c/->uri request
                       "/user/:id/profile/:type/" [id type] {:status 200
                                                             :body (format "ID: %s, Type: %s" id type)}
                       "/user/:id/permissions/"   [id]      {:status 200
                                                             :body (format "ID: %s" id)}))))))
  (testing "Two clauses (no match, custom default)"
    (let [request {:uri "/hello/1234/"}]
      (is (= 404
            (:status (c/->uri request
                       "/user/:id/profile/:type/" [id type] {:status 200
                                                             :body (format "ID: %s, Type: %s" id type)}
                       "/user/:id/permissions/"   [id]      {:status 200
                                                             :body (format "ID: %s" id)}
                       {:status 404
                        :body "Not found"}))))))
  (testing "Two clause (with match)"
    (let [request {:uri "/user/1234/permissions/"}]
      (is (= "ID: 1234"
            (:body (c/->uri request
                     "/user/:id/profile/:type/" [id type] {:status 200
                                                           :body (format "ID: %s, Type: %s" id type)}
                     "/user/:id/permissions/"   [id]      {:status 200
                                                           :body (format "ID: %s" id)})))))))


(deftest test-->method
  (testing "No clause"
    (let [request {:request-method :get}]
      (is (= 405
            (:status (c/->method request))))))
  (testing "One clause (no match)"
    (let [request {:request-method :get}]
      (is (= 405
            (:status (c/->method request
                       :put {:status 200
                             :body   "Updated"}))))))
  (testing "One clause (with match)"
    (let [request {:request-method :get}]
      (is (= 200
            (:status (c/->method request
                       :get {:status 200
                             :body   "Data"}))))))
  (testing "Two clauses (no match)"
    (let [request {:request-method :delete}]
      (is (= 405
            (:status (c/->method request
                       :get {:status 200
                             :body   "Data"}
                       :put {:status 200
                             :body   "Updated"}))))))
  (testing "Two clauses (no match, custom default)"
    (let [request {:request-method :delete}]
      (is (= 404
            (:status (c/->method request
                       :get {:status 200
                             :body   "Data"}
                       :put {:status 200
                             :body   "Updated"}
                       {:status 404
                        :body   "Not found"}))))))
  (testing "Two clauses (with match)"
    (let [request {:request-method :put}]
      (is (= "Updated"
            (:body (c/->method request
                     :get {:status 200
                           :body   "Data"}
                     :put {:status 200
                           :body   "Updated"})))))))


(deftest test-shortcuts
  (let [request-get     {:request-method :get}
        request-head    {:request-method :head}
        request-options {:request-method :options}
        request-put     {:request-method :put}
        request-post    {:request-method :post}
        request-delete  {:request-method :delete}
        ok        {:status 200
                   :body   "Data"}
        not-found {:status 404
                   :body   "Not found"}]
    (testing "->get"
      (is (= 405 (:status (c/->get request-put ok))))
      (is (= 404 (:status (c/->get request-put ok not-found))))
      (is (= 200 (:status (c/->get request-get ok))))
      (is (= 200 (:status (c/->get request-get ok not-found)))))
    (testing "->head"
      (is (= 405 (:status (c/->head request-put ok))))
      (is (= 404 (:status (c/->head request-put ok not-found))))
      (is (= 200 (:status (c/->head request-head ok))))
      (is (= 200 (:status (c/->head request-head ok not-found)))))
    (testing "->options"
      (is (= 405 (:status (c/->options request-put ok))))
      (is (= 404 (:status (c/->options request-put ok not-found))))
      (is (= 200 (:status (c/->options request-options ok))))
      (is (= 200 (:status (c/->options request-options ok not-found)))))
    (testing "->put"
      (is (= 405 (:status (c/->put request-get ok))))
      (is (= 404 (:status (c/->put request-get ok not-found))))
      (is (= 200 (:status (c/->put request-put ok))))
      (is (= 200 (:status (c/->put request-put ok not-found)))))
    (testing "->post"
      (is (= 405 (:status (c/->post request-put ok))))
      (is (= 404 (:status (c/->post request-put ok not-found))))
      (is (= 200 (:status (c/->post request-post ok))))
      (is (= 200 (:status (c/->post request-post ok not-found)))))
    (testing "->delete"
      (is (= 405 (:status (c/->delete request-put ok))))
      (is (= 404 (:status (c/->delete request-put ok not-found))))
      (is (= 200 (:status (c/->delete request-delete ok))))
      (is (= 200 (:status (c/->delete request-delete ok not-found)))))))


(defn composite
  [request]
  (c/->uri request
    "/user/:id/profile/:type/" [id type] (c/->method request
                                           :get {:status 200
                                                 :body (format "Compact profile for ID: %s, Type: %s" id type)}
                                           :put {:status 200
                                                 :body (format "Updated ID: %s, Type: %s" id type)})
    "/user/:id/permissions/"   [id]      (c/->post request {:status 201
                                                          :body "Created new permission"})))


(def composite-fn
  (c/make-uri-handler
    "/user/:id/profile/:type/" (fn [request {:keys [id type]}]
                                 (c/->method request
                                   :get {:status 200
                                         :body (format "Compact profile for ID: %s, Type: %s" id type)}
                                   :put {:status 200
                                         :body (format "Updated ID: %s, Type: %s" id type)}))
    "/user/:id/permissions/"   (fn [request {:keys [id]}]
                                 (c/->post request {:status 201
                                                  :body "Created new permission"}))
    (fn [_] {:status 400
             :headers {"Content-Type" "text/plain"}
             :body "No matching route"})))


(deftest test-composite
  (testing "No route match"
    (is (= 400
          (:status (composite {:request-method :get
                               :uri "/hello/1234/"}))))
    (is (= 400
          (:status (composite-fn {:request-method :get
                                 :uri "/hello/1234/"})))))
  (testing "Matching route and method"
    (is (= "Compact profile for ID: 1234, Type: compact"
          (:body (composite-fn {:request-method :get
                                :uri "/user/1234/profile/compact/"})))))
  (testing "Matching route, but no matching method"
    (is (= 405
          (:status (composite-fn {:request-method :delete
                                  :uri "/user/1234/profile/compact/"}))))))
