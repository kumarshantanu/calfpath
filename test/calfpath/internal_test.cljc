;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.internal-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    #?(:cljs [calfpath.internal :as i :include-macros true]
        :clj [calfpath.internal :as i])))


(deftest test-path-parsing
  (is (= [["/user/" :id "/profile/" :descriptor "/"] false]
        (i/parse-uri-template "/user/:id/profile/:descriptor/")
        (i/parse-uri-template "/user/{id}/profile/{descriptor}/")) "variables with trailing /")
  (is (= [["/user/" :id "/profile/" :descriptor] false]
        (i/parse-uri-template "/user/:id/profile/:descriptor")
        (i/parse-uri-template "/user/{id}/profile/{descriptor}")) "variables without trailing /")
  (is (= [["/foo"] true]
        (i/parse-uri-template "/foo*")) "simple one token, partial")
  (is (= [["/bar/" :bar-id] true]
        (i/parse-uri-template "/bar/:bar-id*")
        (i/parse-uri-template "/bar/{bar-id}*")) "one token, one param, partial")
  (is (= [[""] false]
        (i/parse-uri-template "")) "empty string")
  (is (= [[""] true]
        (i/parse-uri-template "*")) "empty string, partial"))
