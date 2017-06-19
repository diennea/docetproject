# [Docet](http://docetproject.org/)
Docet is an *online documentation manager* for JavaEE applications aiming at devising truly integrated online-help guides. 

Integration of Docet into existing Java applications is intended to be fast and trivial adopting a "covention over configuration" approach.

Documentation is organized around the concept of documentation package, a self-contained unit of documentation pages semantically related, i.e. regarding a specific topic/aspect of the "target application". Accordingly, Docet has been designed as a multi-package documentation manager, where several packages can cohexsist into the same Java application and are conceived so as to be inherently support multi-language documents. Furthermore, packages can be searched via a dedicated keyword/search functionality in order to give application's users the best experience ever when browsing online documentation.

Docet is made of two main "macro" components:
* a Maven plugin and a simple-to-grasp html-like language for realizing actual documentation packages;
* a runtime manager to handle rendering of documentation pages and serving requests regarding any documentation resource and related searches. Rendering of documents comes in two flavors: as *Web page* and/or as *PDF*.

## License

Docet is under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0.html).
