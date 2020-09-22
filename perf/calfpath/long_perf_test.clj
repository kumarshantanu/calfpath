;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.long-perf-test
  "Performance benchmarks for long (OpenSensors) routing table."
  (:require
    [clojure.pprint :as pp]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [ataraxy.core   :as ataraxy]
    [bidi.ring      :as bidi]
    [compojure.core :refer [defroutes rfn routes context GET POST PUT ANY]]
    [clout.core     :as l]
    [reitit.ring    :as reitit]
    [calfpath.core  :as cp :refer [->uri ->method ->get ->head ->options ->put ->post ->delete]]
    [calfpath.internal :as i]
    [calfpath.route :as r]
    [citius.core    :as c]))


(use-fixtures :once
  (c/make-bench-wrapper
    ["Reitit" "CalfPath-core-macros"
     "CalfPath-route-walker" "CalfPath-route-unroll"]
    {:chart-title "Reitit/CalfPath"
     :chart-filename (format "bench-large-routing-table-clj-%s.png" c/clojure-version-str)}))


(defn handler
  [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "OK"})


(defn p-handler
  [params]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (apply str params)})


(defmacro fnpp
  [params]
  (i/expected vector? "params vector" params)
  `(fn [{{:keys ~params} :path-params}]
     (p-handler ~params)))


(def handler-reitit
  (reitit/ring-handler
    (reitit/router
      [["/v2/whoami"                                           {:get handler}]
       ["/v2/users/:user-id/datasets"                          {:get (fnpp [user-id])}]
       ["/v2/public/projects/:project-id/datasets"             {:get (fnpp [project-id])}]
       ["/v1/public/topics/:topic"                             {:get (fnpp [topic])}]
       ["/v1/users/:user-id/orgs/:org-id"                      {:get (fnpp [user-id org-id])}]
       ["/v1/search/topics/:term"                              {:get (fnpp [term])}]
       ["/v1/users/:user-id/invitations"                       {:get (fnpp [user-id])}]
       ["/v1/orgs/:org-id/devices/:batch/:type"                {:get (fnpp [org-id batch type])}]
       ["/v1/users/:user-id/topics"                            {:get (fnpp [user-id])}]
       ["/v1/users/:user-id/bookmarks/followers"               {:get (fnpp [user-id])}]
       ["/v2/datasets/:dataset-id"                             {:get (fnpp [dataset-id])}]
       ["/v1/orgs/:org-id/usage-stats"                         {:get (fnpp [org-id])}]
       ["/v1/orgs/:org-id/devices/:client-id"                  {:get (fnpp [org-id client-id])}]
       ["/v1/messages/user/:user-id"                           {:get (fnpp [user-id])}]
       ["/v1/users/:user-id/devices"                           {:get (fnpp [user-id])}]
       ["/v1/public/users/:user-id"                            {:get (fnpp [user-id])}]
       ["/v1/orgs/:org-id/errors"                              {:get (fnpp [org-id])}]
       ["/v1/public/orgs/:org-id"                              {:get (fnpp [org-id])}]
       ["/v1/orgs/:org-id/invitations"                         {:get (fnpp [org-id])}]
       ;;["/v2/public/messages/dataset/bulk"                     {:get handler}]
       ;;["/v1/users/:user-id/devices/bulk"                      {:get (fnpp [user-id])}]
       ["/v1/users/:user-id/device-errors"                     {:get (fnpp [user-id])}]
       ["/v2/login"                                            {:get handler}]
       ["/v1/users/:user-id/usage-stats"                       {:get (fnpp [user-id])}]
       ["/v2/users/:user-id/devices"                           {:get (fnpp [user-id])}]
       ["/v1/users/:user-id/claim-device/:client-id"           {:get (fnpp [user-id client-id])}]
       ["/v2/public/projects/:project-id"                      {:get (fnpp [project-id])}]
       ["/v2/public/datasets/:dataset-id"                      {:get (fnpp [dataset-id])}]
       ;;["/v2/users/:user-id/topics/bulk"                       {:get (fnpp [user-id])}]
       ["/v1/messages/device/:client-id"                       {:get (fnpp [client-id])}]
       ["/v1/users/:user-id/owned-orgs"                        {:get (fnpp [user-id])}]
       ["/v1/topics/:topic"                                    {:get (fnpp [topic])}]
       ["/v1/users/:user-id/bookmark/:topic"                   {:get (fnpp [user-id topic])}]
       ["/v1/orgs/:org-id/members/:user-id"                    {:get (fnpp [org-id user-id])}]
       ["/v1/users/:user-id/devices/:client-id"                {:get (fnpp [user-id client-id])}]
       ["/v1/users/:user-id"                                   {:get (fnpp [user-id])}]
       ["/v1/orgs/:org-id/devices"                             {:get (fnpp [org-id])}]
       ["/v1/orgs/:org-id/members"                             {:get (fnpp [org-id])}]
       ["/v1/orgs/:org-id/members/invitation-data/:user-id"    {:get (fnpp [org-id user-id])}]
       ["/v2/orgs/:org-id/topics"                              {:get (fnpp [org-id])}]
       ["/v1/whoami"                                           {:get handler}]
       ["/v1/orgs/:org-id"                                     {:get (fnpp [org-id])}]
       ["/v1/users/:user-id/api-key"                           {:get (fnpp [user-id])}]
       ["/v2/schemas"                                          {:get handler}]
       ["/v2/users/:user-id/topics"                            {:get (fnpp [user-id])}]
       ["/v1/orgs/:org-id/confirm-membership/:token"           {:get (fnpp [org-id token])}]
       ["/v2/topics/:topic"                                    {:get (fnpp [topic])}]
       ["/v1/messages/topic/:topic"                            {:get (fnpp [topic])}]
       ["/v1/users/:user-id/devices/:client-id/reset-password" {:get (fnpp [user-id client-id])}]
       ["/v2/topics"                                           {:get handler}]
       ["/v1/login"                                            {:get handler}]
       ["/v1/users/:user-id/orgs"                              {:get (fnpp [user-id])}]
       ["/v2/public/messages/dataset/:dataset-id"              {:get (fnpp [dataset-id])}]
       ["/v1/topics"                                           {:get handler}]
       ["/v1/orgs"                                             {:get handler}]
       ["/v1/users/:user-id/bookmarks"                         {:get (fnpp [user-id])}]
       ["/v1/orgs/:org-id/topics"                              {:get (fnpp [org-id])}]])))


(defn handler-calfpath [request]
  (cp/->uri request
    "/v2/whoami"                                           []                  (cp/->get request (handler request) nil)
    "/v2/users/:user-id/datasets"                          [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/public/projects/:project-id/datasets"             [project-id]        (cp/->get request (p-handler [project-id]) nil)
    "/v1/public/topics/:topic"                             [topic]             (cp/->get request (p-handler [topic]) nil)
    "/v1/users/:user-id/orgs/:org-id"                      [user-id org-id]    (cp/->get request (p-handler [user-id org-id]) nil)
    "/v1/search/topics/:term"                              [term]              (cp/->get request (p-handler [term]) nil)
    "/v1/users/:user-id/invitations"                       [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/orgs/:org-id/devices/:batch/:type"                [org-id batch type] (cp/->get request (p-handler [org-id batch type]) nil)
    "/v1/users/:user-id/topics"                            [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/users/:user-id/bookmarks/followers"               [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/datasets/:dataset-id"                             [dataset-id]        (cp/->get request (p-handler [dataset-id]) nil)
    "/v1/orgs/:org-id/usage-stats"                         [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/orgs/:org-id/devices/:client-id"                  [org-id client-id]  (cp/->get request (p-handler [org-id client-id]) nil)
    "/v1/messages/user/:user-id"                           [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/users/:user-id/devices"                           [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/public/users/:user-id"                            [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/orgs/:org-id/errors"                              [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/public/orgs/:org-id"                              [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/orgs/:org-id/invitations"                         [org-id]            (cp/->get request (p-handler [org-id]) nil)
    ;;"/v2/public/messages/dataset/bulk"                     []                  (cp/->get request (handler request) nil)
    ;;"/v1/users/:user-id/devices/bulk"                      [user-id]           (cp/->get request (handler request) nil)
    "/v1/users/:user-id/device-errors"                     [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/login"                                            []                  (cp/->get request (handler request) nil)
    "/v1/users/:user-id/usage-stats"                       [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/users/:user-id/devices"                           [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/users/:user-id/claim-device/:client-id"           [user-id client-id] (cp/->get request (p-handler [user-id client-id]) nil)
    "/v2/public/projects/:project-id"                      [project-id]        (cp/->get request (p-handler [project-id]) nil)
    "/v2/public/datasets/:dataset-id"                      [dataset-id]        (cp/->get request (p-handler [dataset-id]) nil)
    ;;"/v2/users/:user-id/topics/bulk"                       [user-id]           (cp/->get request (handler request) nil)
    "/v1/messages/device/:client-id"                       [client-id]         (cp/->get request (p-handler [client-id]) nil)
    "/v1/users/:user-id/owned-orgs"                        [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/topics/:topic"                                    [topic]             (cp/->get request (p-handler [topic]) nil)
    "/v1/users/:user-id/bookmark/:topic"                   [user-id topic]     (cp/->get request (p-handler [user-id topic]) nil)
    "/v1/orgs/:org-id/members/:user-id"                    [org-id user-id]    (cp/->get request (p-handler [org-id user-id]) nil)
    "/v1/users/:user-id/devices/:client-id"                [user-id client-id] (cp/->get request (p-handler [user-id client-id]) nil)
    "/v1/users/:user-id"                                   [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/orgs/:org-id/devices"                             [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/orgs/:org-id/members"                             [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/orgs/:org-id/members/invitation-data/:user-id"    [org-id user-id]    (cp/->get request (p-handler [org-id user-id]) nil)
    "/v2/orgs/:org-id/topics"                              [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/whoami"                                           []                  (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id"                                     [org-id]            (cp/->get request (p-handler [org-id]) nil)
    "/v1/users/:user-id/api-key"                           [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/schemas"                                          []                  (cp/->get request (handler request) nil)
    "/v2/users/:user-id/topics"                            [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/orgs/:org-id/confirm-membership/:token"           [org-id token]      (cp/->get request (p-handler [org-id token]) nil)
    "/v2/topics/:topic"                                    [topic]             (cp/->get request (p-handler [topic]) nil)
    "/v1/messages/topic/:topic"                            [topic]             (cp/->get request (p-handler [topic]) nil)
    "/v1/users/:user-id/devices/:client-id/reset-password" [user-id client-id] (cp/->get request (p-handler [user-id client-id]) nil)
    "/v2/topics"                                           []                  (cp/->get request (handler request) nil)
    "/v1/login"                                            []                  (cp/->get request (handler request) nil)
    "/v1/users/:user-id/orgs"                              [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v2/public/messages/dataset/:dataset-id"              [dataset-id]        (cp/->get request (p-handler [dataset-id]) nil)
    "/v1/topics"                                           []                  (cp/->get request (handler request) nil)
    "/v1/orgs"                                             []                  (cp/->get request (handler request) nil)
    "/v1/users/:user-id/bookmarks"                         [user-id]           (cp/->get request (p-handler [user-id]) nil)
    "/v1/orgs/:org-id/topics"                              [org-id]            (cp/->get request (p-handler [org-id]) nil)
    nil))


(defmacro fnp
  [params]
  (i/expected vector? "params vector" params)
  `(fn [{:keys ~params}]
     (p-handler ~params)))


(def opensensors-calfpath-routes
  [{["/v2/whoami"                                           :get] handler}
   {["/v2/users/:user-id/datasets"                          :get] (fnpp [user-id])}
   {["/v2/public/projects/:project-id/datasets"             :get] (fnpp [project-id])}
   {["/v1/public/topics/:topic"                             :get] (fnpp [topic])}
   {["/v1/users/:user-id/orgs/:org-id"                      :get] (fnpp [user-id org-id])}
   {["/v1/search/topics/:term"                              :get] (fnpp [term])}
   {["/v1/users/:user-id/invitations"                       :get] (fnpp [user-id])}
   {["/v1/orgs/:org-id/devices/:batch/:type"                :get] (fnpp [org-id batch type])}
   {["/v1/users/:user-id/topics"                            :get] (fnpp [user-id])}
   {["/v1/users/:user-id/bookmarks/followers"               :get] (fnpp [user-id])}
   {["/v2/datasets/:dataset-id"                             :get] (fnpp [dataset-id])}
   {["/v1/orgs/:org-id/usage-stats"                         :get] (fnpp [org-id])}
   {["/v1/orgs/:org-id/devices/:client-id"                  :get] (fnpp [org-id client-id])}
   {["/v1/messages/user/:user-id"                           :get] (fnpp [user-id])}
   {["/v1/users/:user-id/devices"                           :get] (fnpp [user-id])}
   {["/v1/public/users/:user-id"                            :get] (fnpp [user-id])}
   {["/v1/orgs/:org-id/errors"                              :get] (fnpp [org-id])}
   {["/v1/public/orgs/:org-id"                              :get] (fnpp [org-id])}
   {["/v1/orgs/:org-id/invitations"                         :get] (fnpp [org-id])}
   ;;{["/v2/public/messages/dataset/bulk"                     :get] handler}
   ;;{["/v1/users/:user-id/devices/bulk"                      :get] (fnpp [user-id])}
   {["/v1/users/:user-id/device-errors"                     :get] (fnpp [user-id])}
   {["/v2/login"                                            :get] handler}
   {["/v1/users/:user-id/usage-stats"                       :get] (fnpp [user-id])}
   {["/v2/users/:user-id/devices"                           :get] (fnpp [user-id])}
   {["/v1/users/:user-id/claim-device/:client-id"           :get] (fnpp [user-id client-id])}
   {["/v2/public/projects/:project-id"                      :get] (fnpp [project-id])}
   {["/v2/public/datasets/:dataset-id"                      :get] (fnpp [dataset-id])}
   ;;{["/v2/users/:user-id/topics/bulk"                       :get] (fnpp [user-id])}
   {["/v1/messages/device/:client-id"                       :get] (fnpp [client-id])}
   {["/v1/users/:user-id/owned-orgs"                        :get] (fnpp [user-id])}
   {["/v1/topics/:topic"                                    :get] (fnpp [topic])}
   {["/v1/users/:user-id/bookmark/:topic"                   :get] (fnpp [user-id topic])}
   {["/v1/orgs/:org-id/members/:user-id"                    :get] (fnpp [org-id user-id])}
   {["/v1/users/:user-id/devices/:client-id"                :get] (fnpp [user-id client-id])}
   {["/v1/users/:user-id"                                   :get] (fnpp [user-id])}
   {["/v1/orgs/:org-id/devices"                             :get] (fnpp [org-id])}
   {["/v1/orgs/:org-id/members"                             :get] (fnpp [org-id])}
   {["/v1/orgs/:org-id/members/invitation-data/:user-id"    :get] (fnpp [org-id user-id])}
   {["/v2/orgs/:org-id/topics"                              :get] (fnpp [org-id])}
   {["/v1/whoami"                                           :get] handler}
   {["/v1/orgs/:org-id"                                     :get] (fnpp [org-id])}
   {["/v1/users/:user-id/api-key"                           :get] (fnpp [user-id])}
   {["/v2/schemas"                                          :get] handler}
   {["/v2/users/:user-id/topics"                            :get] (fnpp [user-id])}
   {["/v1/orgs/:org-id/confirm-membership/:token"           :get] (fnpp [org-id token])}
   {["/v2/topics/:topic"                                    :get] (fnpp [topic])}
   {["/v1/messages/topic/:topic"                            :get] (fnpp [topic])}
   {["/v1/users/:user-id/devices/:client-id/reset-password" :get] (fnpp [user-id client-id])}
   {["/v2/topics"                                           :get] handler}
   {["/v1/login"                                            :get] handler}
   {["/v1/users/:user-id/orgs"                              :get] (fnpp [user-id])}
   {["/v2/public/messages/dataset/:dataset-id"              :get] (fnpp [dataset-id])}
   {["/v1/topics"                                           :get] handler}
   {["/v1/orgs"                                             :get] handler}
   {["/v1/users/:user-id/bookmarks"                         :get] (fnpp [user-id])}
   {["/v1/orgs/:org-id/topics"                              :get] (fnpp [org-id])}])


(def compiled-calfpath-routes (r/compile-routes opensensors-calfpath-routes {:show-uris-400? true}))


;; print trie-routes for debugging
;;
;(->> compiled-calfpath-routes
;  (mapv (let [update-when (fn [m k & args]
;                            (if (contains? m k)
;                              (apply update m k args)
;                              m))]
;          (fn cleanup [route]
;            (-> route
;              (dissoc :handler :matcher :matchex :full-uri)
;              (update-when :nested #(when (seq %) (mapv cleanup %)))))))
;  pp/pprint)


(def handler-calfpath-route-walker
  (partial r/dispatch compiled-calfpath-routes))


(def handler-calfpath-route-unroll
  (r/make-dispatcher compiled-calfpath-routes))


(defmacro test-compare-perf
  [bench-name & exprs]
  `(do
     (is (= ~@exprs) ~bench-name)
     (when-not (System/getenv "BENCH_DISABLE")
       (c/compare-perf ~bench-name ~@exprs))))


(deftest test-static-path
  (testing "early"
    (let [request {:request-method :get
                   :uri "/v2/whoami"}]
      (test-compare-perf "(early) static URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))
  (testing "mid"
    (let [request {:request-method :get
                   :uri "/v2/login"}]
      (test-compare-perf "(mid) static URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))
  (testing "late"
    (let [request {:request-method :get
                   :uri "/v1/orgs"}]
      (test-compare-perf "(late) static URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request)))))


(deftest test-dynamic-path
  (testing "early"
    (let [request {:request-method :get
                   :uri "/v2/users/1234/datasets"}]
      (test-compare-perf "(early) dynamic URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request)))
  (testing "mid"
    (let [request {:request-method :get
                   :uri "/v2/public/projects/4567"}]
      (test-compare-perf "(mid) dynamic URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))
  (testing "late"
    (let [request {:request-method :get
                   :uri "/v1/orgs/6789/topics"}]
      (test-compare-perf "(late) dynamic URI"
        (handler-reitit request) (handler-calfpath request)
        (handler-calfpath-route-walker request) (handler-calfpath-route-unroll request))))))
