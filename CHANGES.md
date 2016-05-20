# Changes and TODO


## 0.3.0 / 2016-May-??

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
