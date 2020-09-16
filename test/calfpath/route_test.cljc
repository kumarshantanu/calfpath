;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-test
  (:require
    [clojure.walk :as walk]
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
   {:uri "/foo*"         :nested [{:method :get :uri "" :handler (handler [])}]}
   {:uri "/bar/:bar-id*" :nested [{:method :get :uri "" :handler (handler [:bar-id])}]}
   {:uri "/v1*" :nested [{:uri "/orgs*" :nested [{:uri "/:org-id/topics" :handler (handler [:org-id])}]}]}
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


(def final-routes (r/compile-routes all-routes))


(def pp-final-routes (r/compile-routes pp-all-routes {:params-key :path-params}))


(def final-partial-routes (r/compile-routes all-partial-routes))


(def pp-final-partial-routes (r/compile-routes pp-all-partial-routes {:params-key :path-params}))


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


(defn partial-routes-helper
  [handler body-400]
  (is (= {:request-method :get}
        (handler {:uri "/foo" :request-method :get})) "partial termination - foo")
  (is (= {:request-method :get
          :bar-id "98"}
        (handler {:uri "/bar/98" :request-method :get})) "partial termination - bar")
  (is (= {:request-method :get
          :org-id "87"}
        (handler {:uri "/v1/orgs/87/topics" :request-method :get})) "partial termination - v1/org"))


(defn pp-partial-routes-helper
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
  (testing "walker"
    (routes-helper (partial r/dispatch final-routes) flat-400))
  (testing "walker (path params)"
    (pp-routes-helper (partial r/dispatch pp-final-routes) flat-400))
  (testing "walker partial"
    (routes-helper (partial r/dispatch final-partial-routes) partial-400)
    (partial-routes-helper (partial r/dispatch final-partial-routes) partial-400))
  (testing "walker partial (path params)"
    (pp-routes-helper (partial r/dispatch pp-final-partial-routes) partial-400)
    (pp-partial-routes-helper (partial r/dispatch pp-final-partial-routes) partial-400)))


#?(:clj (deftest test-unrolled
          (testing "unrolled"
            (routes-helper (r/make-dispatcher final-routes) flat-400))
          (testing "unrolled (path params)"
            (pp-routes-helper (r/make-dispatcher pp-final-routes) flat-400))
          (testing "unrolled partial"
            (routes-helper (r/make-dispatcher final-partial-routes) partial-400)
            (partial-routes-helper (r/make-dispatcher final-partial-routes) partial-400))
          (testing "unrolled partial (path params)"
            (pp-routes-helper (r/make-dispatcher pp-final-partial-routes) partial-400)
            (pp-partial-routes-helper (r/make-dispatcher pp-final-partial-routes) partial-400))))


(def pp-handler (handler [:path-params :uri]))


(def easy-routes
  [{["/album/:lid/artist/:rid/" :get] pp-handler}
   {"/hello/1234/"                    pp-handler}
   {["/info/:token/"            :get] pp-handler}
   {"/user/:id*" [{"/auth" pp-handler}
                  {"/permissions/" [{:get  pp-handler}
                                    {:post pp-handler}
                                    {:put  pp-handler}]}
                  {"/profile/:type/" [{:get    pp-handler}
                                      {:patch  pp-handler}
                                      {:delete pp-handler}]}
                  {"" pp-handler}]}])


(def flat-routes
  [{:uri "/info/:token/"            :method :get}
   {:uri "/album/:lid/artist/:rid/" :method :get}
   {:uri "/user/:id/profile/:type/" :nested [{:method :get   }
                                             {:method :patch }
                                             {:method :delete}]}
   {:uri "/user/:id/permissions/"   :nested [{:method :get   }
                                             {:method :post  }
                                             {:method :put   }]}
   {:uri "/user/:id/auth"}
   {:uri "/user/:id"     }
   {:uri "/hello/1234/"  }])


(def trie-routes
  [{:uri "/album/:lid/artist/:rid/" :method :get}
   {:uri "/hello/1234/"                         }
   {:uri "/info/:token/"            :method :get}
   {:uri "/user/:id*" :nested [{:uri "/auth"    }
                               {:uri "/permissions/"   :nested [{:method :get   }
                                                                {:method :post  }
                                                                {:method :put   }]}
                               {:uri "/profile/:type/" :nested [{:method :get   }
                                                                {:method :patch }
                                                                {:method :delete}]}
                               {:uri ""         }]}])


(defn deep-dissoc [data k]
  (walk/prewalk (fn [node]
                  (if (map? node)
                    (dissoc node k)
                    node))
          data))


(deftest test-easy-routes
  (is (= trie-routes
        (deep-dissoc
          (r/easy-routes easy-routes :uri :method)
          :handler))))


(deftest test-routes->wildcard-trie
  (is (= trie-routes
        (-> flat-routes
          (r/update-routes r/routes->wildcard-trie {:trie-threshold 2})))))


(def indexable-routes
  [{:uri "/info/:token"             :method :get :handler identity :id :info}
   {:uri "/album/:lid/artist/:rid/" :method :get :handler identity :id :album}
   {:uri "/user/:id*"
    :nested [{:uri "/auth" :handler identity :id :auth-user}
             {:uri "/permissions/"   :nested [{:method :get    :handler identity :id :read-perms}
                                              {:method :post   :handler identity :id :save-perms}
                                              {:method :put    :handler identity :id :update-perms}]}
             {:uri "/profile/:type/" :nested [{:method :get    :handler identity :id :read-profile}
                                              {:method :patch  :handler identity :id :patch-profile}
                                              {:method :delete :handler identity :id :remove-profile}]}
             {:uri ""                :handler identity }]}])


(deftest test-index-routes
  (let [routing-index (r/make-index indexable-routes)]
    (is (= {:info           {:uri ["/info/" :token]                    :request-method :get}
            :album          {:uri ["/album/" :lid "/artist/" :rid "/"] :request-method :get}
            :auth-user      {:uri ["/user/" :id "/auth"]               :request-method :get}
            :read-perms     {:uri ["/user/" :id "/permissions/"]       :request-method :get}
            :save-perms     {:uri ["/user/" :id "/permissions/"]       :request-method :post}
            :update-perms   {:uri ["/user/" :id "/permissions/"]       :request-method :put}
            :read-profile   {:uri ["/user/" :id "/profile/" :type "/"] :request-method :get}
            :patch-profile  {:uri ["/user/" :id "/profile/" :type "/"] :request-method :patch}
            :remove-profile {:uri ["/user/" :id "/profile/" :type "/"] :request-method :delete}}
          routing-index))
    (is (= {:uri "/album/10/artist/20/"
            :request-method :get}
          (-> (:album routing-index)
            (r/template->request {:lid 10 :rid 20}))))
    (is (= {:uri "/user/10/permissions/"
            :request-method :post}
          (-> (:save-perms routing-index)
            (r/template->request {:id 10}))))
    (is (thrown-with-msg?
          #?(:cljs js/Error
              :clj clojure.lang.ExceptionInfo)
          #"Expected URI param for key \:id, but found .*"
          (-> (:save-perms routing-index)
            (r/template->request {:user-id 10}))))))
