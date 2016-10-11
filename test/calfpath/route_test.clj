;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-test
  (:require
    [clojure.pprint :as pp]
    [clojure.test :refer :all]
    [calfpath.route :as r]))


(defn handler
  [request params]
  (->> params
    (merge {:request-method (:request-method request)})))


(def all-routes
  [{:uri "/info/:token/" :method :get :handler handler :name "info"}
   {:uri "/user/:id/profile/:type/"
    :nested [{:method :get    :handler handler :name "get.user.profile"}
             {:method :patch  :handler handler :name "update.user.profile"}
             {:method :delete :handler handler :name "delete.user.profile"}]}
   {:uri "/user/:id/permissions/"
    :nested [{:method :get    :handler handler :name "get.user.permissions"}
             {:method :post   :handler handler :name "create.user.permission"}
             {:method :put    :handler handler :name "replace.user.permissions"}]}
   {:uri "/hello/1234/" :handler handler}])


(def all-partial-routes
  [{:uri "/info/:token/" :method :get :handler handler :name "info"}
   {:uri "/user/:id*"
    :nested [{:uri "/profile/:type/"
              :nested [{:method :get    :handler handler :name "get.user.profile"}
                       {:method :patch  :handler handler :name "update.user.profile"}
                       {:method :delete :handler handler :name "delete.user.profile"}]}
             {:uri "/permissions/"
              :nested [{:method :get    :handler handler :name "get.user.permissions"}
                       {:method :post   :handler handler :name "create.user.permission"}
                       {:method :put    :handler handler :name "replace.user.permissions"}]}]}
   {:uri "/hello/1234/" :handler handler}])


(def final-routes (r/make-routes all-routes))


(def final-partial-routes (r/make-routes all-partial-routes))


(def flat-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/info/:token/
/user/:id/profile/:type/
/user/:id/permissions/
/hello/1234/")


(def partial-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/info/:token/
/user/:id*
/hello/1234/")


(defn routes-helper
  [handler body-400]
  (is (= {:request-method :get
          :token "status"}
        (handler {:uri "/info/status/" :request-method :get})))
  (is (= {:status 405
          :headers {"Allow" "GET" "Content-Type" "text/plain"}
          :body "405 Method not supported. Allowed methods are: GET"}
        (handler {:uri "/info/status/" :request-method :post})))
  (is (= {:request-method :get
          :id "id-1"
          :type "type-2"}
        (handler {:uri "/user/id-1/profile/type-2/" :request-method :get})))
  (is (= {:request-method :post
          :id "id-2"}
        (handler {:uri "/user/id-2/permissions/"    :request-method :post})))
  (is (= {:request-method :get}
        (handler {:uri "/hello/1234/"               :request-method :get})))
  (is (= {:status 400
          :headers {"Content-Type" "text/plain"}
          :body body-400}
        (handler {:uri "/bad/uri"          :request-method :get})))
  (is (= {:status 405
          :headers {"Allow" "GET, POST, PUT", "Content-Type" "text/plain"}
          :body "405 Method not supported. Allowed methods are: GET, POST, PUT"}
        (handler {:uri "/user/123/permissions/"     :request-method :bad}))))


(deftest test-routes
  (testing "walker"
    (routes-helper (partial r/dispatch final-routes) flat-400))
  (testing "unrolled"
    (routes-helper (r/make-dispatcher final-routes) flat-400))
  (testing "walker partial"
    (routes-helper (partial r/dispatch final-partial-routes) partial-400))
  (testing "unrolled partial"
    (routes-helper (r/make-dispatcher final-partial-routes) partial-400)))
