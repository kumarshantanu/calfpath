;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns calfpath.type
  "Type related arifacts.")


(defprotocol IRouteMatcher
  (-parse-uri-template        [this uri-template])
  (-get-static-uri-template   [this uri-pattern-tokens] "Return URI token(s) if static URI template, nil otherwise")
  (-initialize-request        [this request params-key])
  (-static-uri-partial-match  [this request static-tokens params-key])
  (-static-uri-full-match     [this request static-tokens params-key])
  (-dynamic-uri-partial-match [this request uri-template  params-key])
  (-dynamic-uri-full-match    [this request uri-template  params-key]))
