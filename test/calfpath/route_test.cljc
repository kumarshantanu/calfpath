;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-test
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
  [{:uri "/info/:token/" :method :get :handler (handler [:token]) :name "info"}
   {:uri "/album/:lid/artist/:rid/" :method :get :handler (handler [:lid :rid])}
   {:uri "/user/:id/profile/:type/"
    :nested [{:method :get    :handler (handler [:id :type]) :name "get.user.profile"}
             {:method :patch  :handler (handler [:id :type]) :name "update.user.profile"}
             {:method :delete :handler (handler [:id :type]) :name "delete.user.profile"}]}
   {:uri "/user/:id/permissions/"
    :nested [{:method :get    :handler (handler [:id]) :name "get.user.permissions"}
             {:method :post   :handler (handler [:id]) :name "create.user.permission"}
             {:method :put    :handler (handler [:id]) :name "replace.user.permissions"}]}
   {:uri "/hello/1234/" :handler (handler [])}])


(def pp-all-routes
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
  [{:uri "/info/:token/" :method :get :handler (handler [:token]) :name "info"}
   {:uri "/album/:lid*"
    :nested [{:uri "/artist/:rid/"
              :method :get :handler (handler [:lid :rid])}]}
   {:uri "/user/:id*"
    :nested [{:uri "/profile/:type/"
              :nested [{:method :get    :handler (handler [:id :type]) :name "get.user.profile"}
                       {:method :patch  :handler (handler [:id :type]) :name "update.user.profile"}
                       {:method :delete :handler (handler [:id :type]) :name "delete.user.profile"}]}
             {:uri "/permissions/"
              :nested [{:method :get    :handler (handler [:id]) :name "get.user.permissions"}
                       {:method :post   :handler (handler [:id]) :name "create.user.permission"}
                       {:method :put    :handler (handler [:id]) :name "replace.user.permissions"}]}]}
   {:uri "/hello/1234/" :handler (handler [])}])


(def pp-all-partial-routes
  [{:uri "/info/:token/" :method :get :handler (handler [:path-params]) :name "info"}
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


(def final-routes (r/compile-routes all-routes))


(def pp-final-routes (r/compile-routes pp-all-routes {:params-key :path-params}))


(def final-partial-routes (r/compile-routes all-partial-routes))


(def pp-final-partial-routes (r/compile-routes pp-all-partial-routes {:params-key :path-params}))


(def flat-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/info/:token/
/album/:lid/artist/:rid/
/user/:id/profile/:type/
/user/:id/permissions/
/hello/1234/")


(def partial-400 "400 Bad request. URI does not match any available uri-template.

Available URI templates:
/info/:token/
/album/:lid*
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
          :lid "10"
          :rid "20"}
        (handler {:uri "/album/10/artist/20/" :request-method :get})))
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


(defn pp-routes-helper
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


(deftest test-routes
  (testing "walker"
    (routes-helper (partial r/dispatch final-routes) flat-400))
  (testing "walker (path params)"
    (pp-routes-helper (partial r/dispatch pp-final-routes) flat-400))
  (testing "unrolled"
    (routes-helper (r/make-dispatcher final-routes) flat-400))
  (testing "unrolled (path params)"
    (pp-routes-helper (r/make-dispatcher pp-final-routes) flat-400))
  (testing "walker partial"
    (routes-helper (partial r/dispatch final-partial-routes) partial-400))
  (testing "walker partial (path params)"
    (pp-routes-helper (partial r/dispatch pp-final-partial-routes) partial-400))
  (testing "unrolled partial"
    (routes-helper (r/make-dispatcher final-partial-routes) partial-400))
  (testing "unrolled partial (path params)"
    (pp-routes-helper (r/make-dispatcher pp-final-partial-routes) partial-400)))


(def flat-routes
  [{:uri "/info/:token/"            :method :get  :name "info"}
   {:uri "/album/:lid/artist/:rid/" :method :get }
   {:uri "/user/:id/profile/:type/" :nested [{:method :get     :name "get.user.profile"}
                                             {:method :patch   :name "update.user.profile"}
                                             {:method :delete  :name "delete.user.profile"}]}
   {:uri "/user/:id/permissions/"   :nested [{:method :get     :name "get.user.permissions"}
                                             {:method :post    :name "create.user.permission"}
                                             {:method :put     :name "replace.user.permissions"}]}
   {:uri "/user/:id/auth"           }
   {:uri "/hello/1234/"             }])


(def trie-routes
  [{:uri "/album/:lid/artist/:rid/" :method :get }
   {:uri "/hello/1234/"             }
   {:uri "/info/:token/"            :method :get  :name "info"}
   {:uri "/user/:id*" :nested [{:uri "/auth"           }
                               {:uri "/permissions/"   :nested [{:method :get     :name "get.user.permissions"}
                                                                {:method :post    :name "create.user.permission"}
                                                                {:method :put     :name "replace.user.permissions"}]}
                               {:uri "/profile/:type/" :nested [{:method :get     :name "get.user.profile"}
                                                                {:method :patch   :name "update.user.profile"}
                                                                {:method :delete  :name "delete.user.profile"}]}]}])


(deftest test-routes->wildcard-trie
  (is (= trie-routes
        (-> flat-routes
          (r/update-routes r/routes->wildcard-trie {:trie-threshold 2})))))
