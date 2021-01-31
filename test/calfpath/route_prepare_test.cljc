;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-prepare-test
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


(def er-handler (handler [:path-params :uri :method]))


(def easy-routes
  [{["/album/:lid/artist/:rid/" :get] er-handler}
   {"/hello/1234/"                    er-handler}
   {["/info/:token/"            :get] er-handler}
   {"/user/:id*" [{"/auth" er-handler}
                  {"/permissions/" [{:get  er-handler}
                                    {:post er-handler}
                                    {:put  er-handler}]}
                  {"/profile/:type/" [{:get    er-handler}
                                      {:patch  er-handler}
                                      {:delete er-handler}]}
                  {"" er-handler}]}])


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


(def tidy-routes
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


(def easy-routes2
  [{["/user" :post] er-handler}
   {"/user/:id" [{:get er-handler}
                 {:put er-handler}]}])


(def flat-routes2
  [{:uri "/user" :method :post}
   {:uri "/user/:id" :nested [{:method :get}
                              {:method :put}]}])


(def tidy-routes2
  [{:uri "/user*" :nested [{:uri "/:id" :nested [{:method :get}
                                                 {:method :put}]}
                           {:uri "" :method :post}]}])


(defn deep-dissoc [data k]
  (walk/prewalk (fn [node]
                  (if (map? node)
                    (dissoc node k)
                    node))
          data))


(deftest test-easy-routes
  (is (= tidy-routes
        (deep-dissoc
          (r/easy-routes easy-routes :uri :method)
          :handler)))
  (is (= flat-routes2
        (deep-dissoc
          (r/easy-routes easy-routes2 :uri :method)
          :handler))))


(deftest test-routes->wildcard-tidy
  (is (= tidy-routes
        (-> flat-routes
          (r/update-routes r/routes->wildcard-tidy {:tidy-threshold 2}))))
  (is (= tidy-routes2
        (-> flat-routes2
          (r/update-routes r/routes->wildcard-tidy {:tidy-threshold 1})))))


(def tidy-easy-routes2
  [{["/user" :post] (constantly {:status 200 :body "new-user"})}
   {"/user/:id" [{:get (constantly {:status 200 :body "user-get"})}
                 {:put (constantly {:status 200 :body "user-put"})}]}])
