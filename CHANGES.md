# calfpath Changes and TODO


## TODO

* [TODO - BREAKING CHANGE] Rename the abstraction uri-template to path
* [TODO] Add a middleware to add route to the request map
* [TODO] Allow to construct a URI from URI template and params map
  - Account for partial and nested URI templates


## [WIP] 0.6.0 / 2018-March-??

* [BREAKING CHANGE] Drop support for Clojure versions 1.5 and 1.6
  * Supported Clojure versions: 1.7, 1.8, 1.9
* Put path params under an optional key in request map (by adding pair `:uri-params <request-key>` to route spec)
  * [BREAKING CHANGE] Update `calfpath.route/make-uri-matcher` arity - accept an extra argument `uri-params-key`
  * [BREAKING CHANGE] In middleware `lift-key-middleware` accept `lift-keys` collection instead of single `lift-key`
* Support for asynchronous Ring handlers in routes API
* Performance optimization
  * Make fallback matches faster with matchex optimization
  * [Todo] Make keyword method matches faster using `identical?` instead of `=`
* Middleware
  * [Todo] `add-uri-params-key` - add path-params key to all URI routes
  * [Todo] `decode-uri-params`  - apply url-decode to URI params (not applied by default)
  * [Todo] `uri-trailing-slash` - drop or add trailing slash to non-partial URI matchers
* [Todo] Bi-directional route support (CLJS compatible)
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
