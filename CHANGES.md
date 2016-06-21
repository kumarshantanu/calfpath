# Changes and TODO


## TODO

* Make URI-match work for partial matches and URI prefixes
  * A partial URI pattern may be expressed with a `*` suffix
  * Zero or more partial URI patterns may exist in a route tree
  * This may impact how URIs in HTTP-400 responses are generated
* Add support for fetching/rendering static files
  * Rendering should be configurable (default: fn that returns file-content as body)


## 0.4.0 / 2016-June-??

* Helper fn to build routes from given route specs
  * [TODO] Support for splitting routes with URI and method specifiers into nested routes
* [TODO] A truthy `(true? matcher)` should result in direct dispatch


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
