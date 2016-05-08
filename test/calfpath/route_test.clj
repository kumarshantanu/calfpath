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
  [{:uri "/user/:id/profile/:type/"
    :nested [{:method :get    :handler handler :name "get.user.profile"}
             {:method :patch  :handler handler :name "update.user.profile"}
             {:method :delete :handler handler :name "delete.user.profile"}]}
   {:uri "/user/:id/permissions/"
    :nested [{:method :get    :handler handler :name "get.user.permissions"}
             {:method :post   :handler handler :name "create.user.permission"}
             {:method :put    :handler handler :name "replace.user.permissions"}]}
   {:uri "/hello/1234/" :handler handler}])


(def final-routes (-> all-routes
                    (r/update-routes r/update-fallback-405 :method)
                    (r/update-routes r/update-fallback-400 :uri {:show-uris? true})
                    (r/update-each-route r/make-method-matcher :method)
                    (r/update-each-route r/make-uri-matcher :uri)))


(defn routes-helper
  [handler]
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
          :body "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/user/:id/profile/:type/
/user/:id/permissions/
/hello/1234/"}
        (handler {:uri "/bad/uri"          :request-method :get})))
  (is (= {:status 405
          :headers {"Allow" "GET, POST, PUT", "Content-Type" "text/plain"}
          :body "405 Method not supported. Allowed methods are: GET, POST, PUT"}
        (handler {:uri "/user/123/permissions/"     :request-method :bad}))))


(deftest test-routes
;  (testing "walker"
;    (routes-helper (partial r/dispatch final-routes)))
  (testing "unrolled"
    (routes-helper (r/make-dispatcher final-routes))))
