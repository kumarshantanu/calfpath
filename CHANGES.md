# calfpath Changes and TODO


## TODO

* [TODO - BREAKING CHANGE] Rename the abstraction uri-template to path
* [TODO] Route based URI-generation from URI pattern (in a route) and params map
  - `:id` attribute (in route, later associated with `:handler`) based
  - CLJS compatible (implies: JVM code must stay away in routes definition)
  - Account for partial and nested URI templates


## [WIP] 0.8.0 / 2019-January-??

* [WIP] Automatic prefix-segmentation using wildcard (performance)
* [WIP] Add large routes (OpenSensors) to performance benchmarks
* [TODO - BREAKING CHANGE] Rename `assoc-spec-to-request` to `assoc-route-to-request` in `calfpath.route`


## 0.7.2 / 2019-January-15

* Add a middleware to add route to the request map
  - `calfpath.route/assoc-spec-to-request-middleware`


## 0.7.1 / 2019-January-04

* Routes
  - Add utility fn `calfpath.route/prewalk-routes`
  - Fix reporting "URI templates" in routes fallback-400 handler
    - Introduce `:full-uri` kwarg in `calfpath.route/compile-routes` as reference key


## 0.7.0 / 2019-January-03

* Routes
  * [BREAKING CHANGE] Allow argument `params-key` instead of looking up route spec
    * Accept `params-key` in `calfpath.route/make-uri-matcher` - no route spec lookup
    * Changes to `calfpath.route/compile-routes`
      * Drop support for kwargs `:split-params?` and `:uri-params-key`
      * Accept optional kwarg `:params-key`
* Performance
  * Include [Reitit](https://github.com/metosin/reitit) among performance benchmarks
  * Avoid allocating MatchResult object on route full-match with no params
    * [IMPL CHANGE] Drop `MatchResult.fullMatch()` in favour of `MatchResult.FULL_MATCH_NO_PARAMS`
  * Allocate param map (in `Util.matchURI()`) to hold only as many params as likely


## 0.6.0 / 2018-April-30

* [BREAKING CHANGE] Drop support for Clojure versions 1.5 and 1.6
  * Supported Clojure versions: 1.7, 1.8, 1.9
* [BREAKING CHANGE] Rename `calfpath.route/make-routes` to `calfpath.route/compile-routes`
* Routes: Put URI params under an optional key in request map (by adding pair `:uri-params <request-key>` to route)
  * [BREAKING CHANGE] Update `calfpath.route/make-uri-matcher` arity - accept an extra argument `uri-params-key`
  * [BREAKING CHANGE] In middleware `lift-key-middleware` accept `lift-keys` collection instead of single `lift-key`
  * Refactor `calfpath.route/compile-routes`
    * Add option kwargs
      * `:uri-params-key` to find out where to place URI params in the request map
      * `:uri-params-val` to specify where to place URI params in the request map
      * `:split-params?` to determine whether to split URI params under a separate key in request map
      * `:trailing-slash` to specify action to perform with trailing slash (`:add` or `:remove`) to URI patterns
  * Workaround for `conj` bug in Aleph (0.4.4) and Immutant (2.1.10) requests
    * https://github.com/ztellman/aleph/issues/374
    * https://issues.jboss.org/browse/IMMUTANT-640
* Support for asynchronous Ring handlers in routes API
* Performance optimization
  * Make fallback matches faster with matchex optimization
  * Make keyword method matches faster using `identical?` instead of `=`
* Route middleware
  * `calfpath.route/assoc-kv-middleware` - associate key/value pairs corresponding to a main key in a route
  * `calfpath.route/trailing-slash-middleware` - drop or add trailing slash to non-partial URI matchers
* Overhaul performance benchmarks
  * Use external handler fns in routing code
  * Fix parameter extraction with Clout
  * Add benchmarks for other routing libraries
    * Ataraxy
    * Bidi


## 0.5.0 / 2017-December-10

* Routes
  * [BREAKING CHANGE] Matcher now returns potentially-updated request, or `nil`
  * [BREAKING CHANGE] Route handler now has the same arity as Ring handler
    * Path-params associated in request map under respective keys
    * This allows Ring middleware to be applied to route handlers
  * [BREAKING CHANGE] Drop `ring-handler-middleware`
* Documentation
  * Fetching/rendering static files or classpath resources using Ring middleware
  * Documentation for routes (keys other than essential ones)


## 0.4.0 / 2016-October-13

* Make URI-match work for partial matches and URI prefixes
  * A partial URI pattern may be expressed with a `*` suffix
  * Zero or more partial URI patterns may exist in a route tree
  * This may impact how URIs in HTTP-400 responses are generated
* Middleware
  * A helper fn `calfpath.route/update-in-each-route` to apply route attribute wrapper to specs
  * A lift-key middleware `calfpath.route/lift-key-middleware` to split routes with mixed specs
  * A ring-route middleware `calfpath.route/ring-handler-middleware` to wrap Ring handlers into route handlers
* Helper fn `calfpath.route/make-routes` to build routes from given route specs
* Allow non-literal string URI-patterns in `calfpath.core/->uri`
* Fix `calfpath.route/update-fallback-400` to add fallback 400 route on one or more URI entry, instead of all
* BREAKING CHANGE: Drop `calfpath.core/make-uri-handler` in favor of Calfpath routes


## 0.3.0 / 2016-May-27

* Support for extensible routes as a first-class abstraction
  * A dispatcher fn that walks given routes to match request and invoke corresponding handler
  * An optimized (through loop unrolling) way to create a dispatcher fn from given routes
  * Helper fns to manipulate routes at shallow and deep levels


## 0.2.1 / 2016-February-17

* Add support for `PATCH` HTTP method


## 0.2.0 / 2015-June-06

* Dispatch (fn) on URI template by calling fns, that returns a Ring handler fn


## 0.1.0 / 2015-June-04

* Dispatch (macro) on URI template by evaluating expression in lexical scope
* Dispatch (macro) on HTTP method by evaluating expression
