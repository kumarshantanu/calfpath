;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.route.defaults
  "Internal namespace for data-driven route defaults."
  (:require
    [calfpath.type :as t]
    [calfpath.route.uri-index-match :as uim]
    [calfpath.route.uri-token-match :as utm]))


(def router utm/route-matcher)


(defn d-parse-uri-template [uri-pattern]
  (t/-parse-uri-template router uri-pattern))


(defn d-get-static-uri-template [uri-pattern-tokens]
  (t/-get-static-uri-template router uri-pattern-tokens))


(defn d-initialize-request [request params-key]
  (t/-initialize-request router request params-key))


(defn d-static-uri-partial-match
  [request static-tokens params-key]
  (t/-static-uri-partial-match  router request static-tokens params-key))


(defn d-static-uri-full-match
  [request static-tokens params-key]
  (t/-static-uri-full-match     router request static-tokens params-key))


(defn d-dynamic-uri-partial-match
  [request uri-template params-key]
  (t/-dynamic-uri-partial-match router request uri-template params-key))


(defn d-dynamic-uri-full-match
  [request uri-template params-key]
  (t/-dynamic-uri-full-match    router request uri-template params-key))
