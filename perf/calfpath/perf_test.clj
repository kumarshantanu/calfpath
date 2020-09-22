;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.perf-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [ataraxy.core   :as ataraxy]
    [bidi.ring      :as bidi]
    [compojure.core :refer [defroutes rfn routes context GET POST PUT ANY]]
    [clout.core     :as l]
    [reitit.ring    :as reitit]
    [calfpath.core  :refer [->uri ->method ->get ->head ->options ->put ->post ->delete]]
    [calfpath.internal :as i]
    [calfpath.route :as r]
    [citius.core    :as c]))


(defn h11 [id type] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".11." type)})
(defn h12 [id type] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".12." type)})
(defn h1x []        {:status 405
                     :headers {"Allow" "GET, PUT"
                               "Content-Type" "text/plain"}
                     :body "405 Method not supported. Supported methods are: GET, PUT"})


(defn h21 [id]      {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".21")})
(defn h22 [id]      {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".22")})
(defn h2x []        {:status 405
                     :headers {"Allow" "GET, PUT"
                               "Content-Type" "text/plain"}
                     :body "405 Method not supported. Supported methods are: GET, PUT"})
(defn h30 [cid did] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str cid ".3." did)})
(defn h3x []        {:status 405
                     :headers {"Allow" "PUT"
                               "Content-Type" "text/plain"}
                     :body "405 Method not supported. Only PUT is supported."})
(defn h40 []        {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body "4"})
(defn h4x []        {:status 405
                     :headers {"Allow" "PUT"
                               "Content-Type" "text/plain"}
                     :body "405 Method not supported. Only PUT is supported."})
(defn hxx []        {:status 400
                     :headers {"Content-Type" "text/plain"}
                     :body "400 Bad request. URI does not match any available uri-template."})

(def handler-ataraxy
 (ataraxy/handler
   {:routes '{["/user/" id "/profile/" type "/"] {:get [:h11 id type] :put [:h12 id type] "" [:h1x]}
              ["/user/" id "/permissions/"]      {:get [:h21 id] :put [:h22 id] "" [:h2x]}
              ["/company/" cid "/dept/" did "/"] {:put [:h30] "" [:h3x]}
              "/this/is/a/static/route"          {:put [:h40] "" [:h4x]}
              ^{:re #".*"} path                  [:hxx]}
    :handlers {:h11 (fn [{{:keys [id type]} :route-params}] (h11 id type))
               :h12 (fn [{{:keys [id type]} :route-params}] (h12 id type))
               :h1x (fn [_] (h1x))
               :h21 (fn [{{:keys [id]} :route-params}] (h21 id))
               :h22 (fn [{{:keys [id]} :route-params}] (h22 id))
               :h2x (fn [_] (h2x))
               :h30 (fn [{{:keys [cid did]} :route-params}] (h30 cid did))
               :h3x (fn [_] (h3x))
               :h40 (fn [_] (h40))
               :h4x (fn [_] (h4x))
               :hxx (fn [_] (hxx))}}))


(def handler-bidi
  ;; path params are in (:route-params request)
  (bidi/make-handler
    ["/" {"user/" {[:id "/profile/" :type "/"] {:get (fn [{{:keys [id type]} :route-params}] (h11 id type))
                                                :put (fn [{{:keys [id type]} :route-params}] (h12 id type))
                                                true (fn [_] (h1x))}
                   [:id "/permissions/"]       {:get (fn [{{:keys [id]} :route-params}] (h21 id))
                                                :put (fn [{{:keys [id]} :route-params}] (h22 id))
                                                true (fn [_] (h2x))}}
          ["company/" :cid "/dept/" :did "/"]  {:put (fn [{{:keys [cid did]} :route-params}] (h30 cid did))
                                                true (fn [_] (h3x))}
          "this/is/a/static/route"             {:put (fn [_] (h40))
                                                true (fn [_] (h4x))}
          true                                 (fn [_] (hxx))}]))


(defroutes handler-compojure
  (context "/user/:id/profile/:type/" [id type]
    (GET "/" request (h11 id type))
    (PUT "/" request (h12 id type))
    (ANY "/" request (h1x)))
  (context "/user/:id/permissions"    [id]
    (GET "/" request (h21 id))
    (PUT "/" request (h22 id))
    (ANY "/" request (h2x)))
  (context "/company/:cid/dept/:did"  [cid did]
    (PUT "/" request (h30 cid did))
    (ANY "/" request (h3x)))
  (context "/this/is/a/static/route"  []
    (PUT "/" request (h40))
    (ANY "/" request (h4x)))
  (rfn request (hxx)))


(def handler-reitit
  (reitit/ring-handler
    (reitit/router
      [["/user/:id/profile/:type/" {:get (fn [{{:keys [id type]} :path-params}] (h11 id type))
                                    :put (fn [{{:keys [id type]} :path-params}] (h12 id type))}]
       ["/user/:id/permissions/"   {:get (fn [{{:keys [id]} :path-params}] (h21 id))
                                    :put (fn [{{:keys [id]} :path-params}] (h22 id))}]
       ["/company/:cid/dept/:did/" {:put (fn [{{:keys [cid did]} :path-params}] (h30 cid did))}]
       ["/this/is/a/static/route"  {:put (fn [_] (h40))}]])
    (fn [request]
      (case (get-in request [:reitit.core/match :template])
        "/user/:id/profile/:type/" (h1x)
        "/user/:id/permissions/"   (h2x)
        "/company/:cid/dept/:did/" (h3x)
        "/this/is/a/static/route"  (h4x)
        (hxx)))))


(defmacro cond-let
  [& clauses]
  (i/expected (comp odd? count) "odd number of clauses" clauses)
  (if (= 1 (count clauses))
    (first clauses)
    `(if-let ~(first clauses)
       ~(second clauses)
       (cond-let ~@(drop 2 clauses)))))


(let [uri-1 (l/route-compile "/user/:id/profile/:type/")
      uri-2 (l/route-compile "/user/:id/permissions/")
      uri-3 (l/route-compile "/company/:cid/dept/:did/")
      uri-4 (l/route-compile "/this/is/a/static/route")]
  (defn handler-clout
    [request]
    (cond-let
      [{:keys [id type]} (l/route-matches uri-1 request)] (case (:request-method request)
                                                            :get (h11 id type)
                                                            :put (h12 id type)
                                                            (h1x))
      [{:keys [id]} (l/route-matches uri-2 request)]      (case (:request-method request)
                                                            :get (h21 id)
                                                            :put (h22 id)
                                                            (h2x))
      [{:keys [cid did]} (l/route-matches uri-3 request)] (if (identical? :put (:request-method request))
                                                            (h30 cid did)
                                                            (h3x))
      [_ (l/route-matches uri-4 request)]                 (if (identical? :put (:request-method request))
                                                            (h40)
                                                            (h4x))
      (hxx))))


(defn handler-calfpath
  [request]
  (->uri request
    "/user/:id/profile/:type/" [id type] (->method request
                                           :get (h11 id type)
                                           :put (h12 id type)
                                           (h1x))
    "/user/:id/permissions/"   [id]      (->method request
                                           :get (h21 id)
                                           :put (h22 id)
                                           (h2x))
    "/company/:cid/dept/:did/" [cid did] (->put request (h30 cid did) (h3x))
    "/this/is/a/static/route"  []        (->put request (h40) (h4x))
    (hxx)))


(def calfpath-routes
  [{"/user/:id/profile/:type/" [{:get (fn [{{:keys [id type]} :path-params}] (h11 id type))}
                                {:put (fn [{{:keys [id type]} :path-params}] (h12 id type))}
                                {:matcher identity :handler (fn [_] (h1x))}]}
   {:uri "/user/:id/permissions/"   :nested [{:method :get :handler (fn [{{:keys [id]} :path-params}] (h21 id))}
                                             {:method :put :handler (fn [{{:keys [id]} :path-params}] (h22 id))}
                                             {:matcher identity :handler (fn [_] (h2x))}]}
   {"/company/:cid/dept/:did/" [{:put (fn [{{:keys [cid did]} :path-params}] (h30 cid did))}
                                {:matcher identity :handler (fn [_] (h3x))}]}
   {"/this/is/a/static/route"  [{:put (fn [request] (h40))}
                                {:matcher identity :handler (fn [_] (h4x))}]}
   {:matcher identity :handler (fn [_] (hxx))}])


(def compiled-calfpath-routes (r/compile-routes calfpath-routes {:show-uris-400? false}))


(def handler-calfpath-route-walker
  (partial r/dispatch compiled-calfpath-routes))


(def handler-calfpath-route-unroll
  (r/make-dispatcher compiled-calfpath-routes))


(use-fixtures :once
  (c/make-bench-wrapper
    ["Ataraxy" "Bidi" "Compojure" "Clout" "Reitit" "CalfPath-macros" "CalfPath-route-walker" "CalfPath-route-unroll"]
    {:chart-title "Ataraxy/Bidi/Compojure/Clout/Reitit/CalfPath"
     :chart-filename (format "bench-small-routing-table-clj-%s.png" c/clojure-version-str)}))


(defmacro test-compare-perf
  [bench-name & exprs]
  `(do
     (is (= ~@exprs) ~bench-name)
     (when-not (System/getenv "BENCH_DISABLE")
       (c/compare-perf ~bench-name ~@exprs))))


(deftest test-no-match
  (testing "no URI match"
    (let [request {:request-method :get
                   :uri "/hello/joe/"}]
      (test-compare-perf "no URI match"
        (handler-ataraxy request) (handler-bidi request) (handler-compojure request) (handler-clout request)
        (handler-reitit request)
        (handler-calfpath request) (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))
  (testing "no method match"
    (let [request {:request-method :post
                   :uri "/user/1234/profile/compact/"}]
      (test-compare-perf "no method match"
        (handler-ataraxy request) (handler-bidi request) (handler-compojure request) (handler-clout request)
        (handler-reitit request)
        (handler-calfpath request) (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request)))))


(deftest test-match
  (testing "static route match"
    (let [request {:request-method :put
                   :uri "/this/is/a/static/route"}]
      (test-compare-perf "static URI match, 1 method"
        (handler-ataraxy request) (handler-bidi request) (handler-compojure request) (handler-clout request)
        (handler-reitit request)
        (handler-calfpath request) (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))
  (testing "pattern route match"
    (let [request {:request-method :get
                   :uri "/user/1234/profile/compact/"}]
      (test-compare-perf "pattern URI match, 2 methods"
        (handler-ataraxy request) (handler-bidi request) (handler-compojure request) (handler-clout request)
        (handler-reitit request)
        (handler-calfpath request) (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request)))
    (let [request {:request-method :get
                   :uri "/company/1234/dept/5678/"}]
      (test-compare-perf "pattern URI match, 1 method"
        (handler-ataraxy request) (handler-bidi request) (handler-compojure request) (handler-clout request)
        (handler-reitit request)
        (handler-calfpath request) (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request)))))
