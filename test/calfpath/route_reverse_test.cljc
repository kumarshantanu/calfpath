;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route-reverse-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    #?(:cljs [calfpath.route :as r :include-macros true]
        :clj [calfpath.route :as r])))


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
             {:uri ""                :handler identity }]}
   {:uri "/public/*" :method :get :handler identity :id :public-file}])


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
            :remove-profile {:uri ["/user/" :id "/profile/" :type "/"] :request-method :delete}
            :public-file    {:uri ["/public/" :*]                      :request-method :get}}
          routing-index))
    (is (= {:uri "/album/10/artist/20/"
            :request-method :get}
          (-> (:album routing-index)
            (r/template->request {:uri-params {:lid 10 :rid 20}}))))
    (is (= {:uri "/public/foo.html"
            :request-method :get}
          (-> (:public-file routing-index)
            (r/template->request {:uri-params {:* "foo.html"}}))))
    (is (= {:uri "https://myapp.com/user/10/permissions/?q=beer&country=in"
            :request-method :post}
          (-> (:save-perms routing-index)
            (r/template->request {:uri-params {:id 10}
                                  :uri-prefix "https://myapp.com"
                                  :uri-suffix "?q=beer&country=in"}))))
    (is (thrown-with-msg?
          #?(:cljs js/Error
              :clj clojure.lang.ExceptionInfo)
          #"Expected URI param for key \:id, but found .*"
          (-> (:save-perms routing-index)
            (r/template->request {:uri-params {:user-id 10}}))))))
