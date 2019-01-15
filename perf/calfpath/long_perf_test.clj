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


;; route: namespace


(defn handler [] {:status 200
                  :headers {"Content-Type" "text/plain"}
                  :body "OK"})


(defn opensensors-calfpath-macro-handler [request]
  (cp/->uri
    request
    "/v2/whoami" [] (cp/->get request (handler request) nil)
    "/v2/users/:user-id/datasets" [] (cp/->get request (handler request) nil)
    "/v2/public/projects/:project-id/datasets" [] (cp/->get request (handler request) nil)
    "/v1/public/topics/:topic" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/orgs/:org-id" [] (cp/->get request (handler request) nil)
    "/v1/search/topics/:term" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/invitations" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/devices/:batch/:type" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/topics" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/bookmarks/followers" [] (cp/->get request (handler request) nil)
    "/v2/datasets/:dataset-id" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/usage-stats" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/devices/:client-id" [] (cp/->get request (handler request) nil)
    "/v1/messages/user/:user-id" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/devices" [] (cp/->get request (handler request) nil)
    "/v1/public/users/:user-id" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/errors" [] (cp/->get request (handler request) nil)
    "/v1/public/orgs/:org-id" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/invitations" [] (cp/->get request (handler request) nil)
    "/v2/public/messages/dataset/bulk" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/devices/bulk" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/device-errors" [] (cp/->get request (handler request) nil)
    "/v2/login" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/usage-stats" [] (cp/->get request (handler request) nil)
    "/v2/users/:user-id/devices" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/claim-device/:client-id" [] (cp/->get request (handler request) nil)
    "/v2/public/projects/:project-id" [] (cp/->get request (handler request) nil)
    "/v2/public/datasets/:dataset-id" [] (cp/->get request (handler request) nil)
    "/v2/users/:user-id/topics/bulk" [] (cp/->get request (handler request) nil)
    "/v1/messages/device/:client-id" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/owned-orgs" [] (cp/->get request (handler request) nil)
    "/v1/topics/:topic" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/bookmark/:topic" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/members/:user-id" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/devices/:client-id" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/devices" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/members" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/members/invitation-data/:user-id" [] (cp/->get request (handler request) nil)
    "/v2/orgs/:org-id/topics" [] (cp/->get request (handler request) nil)
    "/v1/whoami" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/api-key" [] (cp/->get request (handler request) nil)
    "/v2/schemas" [] (cp/->get request (handler request) nil)
    "/v2/users/:user-id/topics" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/confirm-membership/:token" [] (cp/->get request (handler request) nil)
    "/v2/topics/:topic" [] (cp/->get request (handler request) nil)
    "/v1/messages/topic/:topic" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/devices/:client-id/reset-password" [] (cp/->get request (handler request) nil)
    "/v2/topics" [] (cp/->get request (handler request) nil)
    "/v1/login" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/orgs" [] (cp/->get request (handler request) nil)
    "/v2/public/messages/dataset/:dataset-id" [] (cp/->get request (handler request) nil)
    "/v1/topics" [] (cp/->get request (handler request) nil)
    "/v1/orgs" [] (cp/->get request (handler request) nil)
    "/v1/users/:user-id/bookmarks" [] (cp/->get request (handler request) nil)
    "/v1/orgs/:org-id/topics" [] (cp/->get request (handler request) nil)
    nil))


(def opensensors-calfpath-routes
  [{:uri "/v2/whoami" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/users/:user-id/datasets" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/public/projects/:project-id/datasets" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/public/topics/:topic" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/orgs/:org-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/search/topics/:term" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/invitations" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/devices/:batch/:type" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/topics" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/bookmarks/followers" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/datasets/:dataset-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/usage-stats" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/devices/:client-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/messages/user/:user-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/devices" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/public/users/:user-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/errors" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/public/orgs/:org-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/invitations" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/public/messages/dataset/bulk" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/devices/bulk" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/device-errors" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/login" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/usage-stats" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/users/:user-id/devices" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/claim-device/:client-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/public/projects/:project-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/public/datasets/:dataset-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/users/:user-id/topics/bulk" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/messages/device/:client-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/owned-orgs" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/topics/:topic" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/bookmark/:topic" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/members/:user-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/devices/:client-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/devices" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/members" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/members/invitation-data/:user-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/orgs/:org-id/topics" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/whoami" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/api-key" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/schemas" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/users/:user-id/topics" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/confirm-membership/:token" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/topics/:topic" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/messages/topic/:topic" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/devices/:client-id/reset-password" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/topics" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/login" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/orgs" :nested [{:method :get, :handler handler}]}
   {:uri "/v2/public/messages/dataset/:dataset-id" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/topics" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/users/:user-id/bookmarks" :nested [{:method :get, :handler handler}]}
   {:uri "/v1/orgs/:org-id/topics" :nested [{:method :get, :handler handler}]}])
