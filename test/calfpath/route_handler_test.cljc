;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-handler-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    #?(:cljs [calfpath.route :as r :include-macros true]
        :clj [calfpath.route :as r])))


(defn handler
  [ks]
  (fn [request]
    (select-keys request (conj ks :request-method))))


(def all-routes
  [{:uri "/info/:token/"            :method :get :handler (handler [:path-params]) :name "info"}
   {:uri "/album/:lid/artist/:rid/" :method :get :handler (handler [:path-params])}
   {:uri "/user/:id/profile/:type/"
    :nested [{:method :get    :handler (handler [:path-params]) :name "get.user.profile"}
             {:method :patch  :handler (handler [:path-params]) :name "update.user.profile"}
             {:method :delete :handler (handler [:path-params]) :name "delete.user.profile"}]}
   {:uri "/user/:id/permissions/"
    :nested [{:method :get    :handler (handler [:path-params]) :name "get.user.permissions"}
             {:method :post   :handler (handler [:path-params]) :name "create.user.permission"}
             {:method :put    :handler (handler [:path-params]) :name "replace.user.permissions"}]}
   {:uri "/hello/1234/" :handler (handler [])}])


(def all-partial-routes
  [{:uri "/info/:token/" :method :get :handler (handler [:path-params]) :name "info"}
   {:uri "/foo*"         :nested [{:method :get :uri "" :handler (handler [])}]}
   {:uri "/bar/:bar-id*" :nested [{:method :get :uri "" :handler (handler [:path-params])}]}
   {:uri "/v1*" :nested [{:uri "/orgs*" :nested [{:uri "/:org-id/topics" :handler (handler [:path-params])}]}]}
   {:uri "/album/:lid*"
    :nested [{:uri "/artist/:rid/"
              :method :get :handler (handler [:path-params])}]}
   {:uri "/user/:id*"
    :nested [{:uri "/profile/:type/"
              :nested [{:method :get    :handler (handler [:path-params]) :name "get.user.profile"}
                       {:method :patch  :handler (handler [:path-params]) :name "update.user.profile"}
                       {:method :delete :handler (handler [:path-params]) :name "delete.user.profile"}]}
             {:uri "/permissions/"
              :nested [{:method :get    :handler (handler [:path-params]) :name "get.user.permissions"}
                       {:method :post   :handler (handler [:path-params]) :name "create.user.permission"}
                       {:method :put    :handler (handler [:path-params]) :name "replace.user.permissions"}]}]}
   {:uri "/hello/1234/" :handler (handler [])}])


(def final-routes (r/compile-routes all-routes {:params-key :path-params}))


(def final-partial-routes (r/compile-routes all-partial-routes {:params-key :path-params}))


(def flat-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/album/:lid/artist/:rid/
/hello/1234/
/info/:token/
/user/:id*")


(def partial-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/album/:lid*
/bar/:bar-id*
/foo*
/hello/1234/
/info/:token/
/user/:id*
/v1*")


(defn routes-helper
  [handler body-400]
  (is (= {:request-method :get
          :path-params {:token "status"}}
        (handler {:uri "/info/status/" :request-method :get})))
  (is (= {:status 405
          :headers {"Allow" "GET" "Content-Type" "text/plain"}
          :body "405 Method not supported. Allowed methods are: GET"}
        (handler {:uri "/info/status/" :request-method :post})))
  (is (= {:request-method :get
          :path-params {:lid "10"
                        :rid "20"}}
        (handler {:uri "/album/10/artist/20/" :request-method :get})))
  (is (= {:request-method :get
          :path-params {:id "id-1"
                        :type "type-2"}}
        (handler {:uri "/user/id-1/profile/type-2/" :request-method :get})))
  (is (= {:request-method :post
          :path-params {:id "id-2"}}
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


(defn partial-routes-helper
  [handler body-400]
  (is (= {:request-method :get}
        (handler {:uri "/foo" :request-method :get})) "partial termination - foo")
  (is (= {:request-method :get
          :path-params {:bar-id "98"}}
        (handler {:uri "/bar/98" :request-method :get})) "partial termination - bar")
  (is (= {:request-method :get
          :path-params {:org-id "87"}}
        (handler {:uri "/v1/orgs/87/topics" :request-method :get})) "partial termination - v1/org"))


(deftest test-walker
  (testing "walker (path params)"
    (routes-helper (partial r/dispatch final-routes) flat-400))
  (testing "walker partial (path params)"
    (routes-helper (partial r/dispatch final-partial-routes) partial-400)
    (partial-routes-helper (partial r/dispatch final-partial-routes) partial-400)))


#?(:clj (deftest test-unrolled
          (testing "unrolled (path params)"
            (routes-helper (r/make-dispatcher final-routes) flat-400))
          (testing "unrolled partial (path params)"
            (routes-helper (r/make-dispatcher final-partial-routes) partial-400)
            (partial-routes-helper (r/make-dispatcher final-partial-routes) partial-400))))
