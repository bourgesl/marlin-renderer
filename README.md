Marlin-renderer
===============

Marlin is an open source (GPL2 + CP) Java2D ``RenderingEngine`` optimized for performance (improved memory usage (~ no GC) and footprint, better multi-threading) and better visual quality based on openjdk's pisces implementation.
It handles shape rendering (``Graphics2D draw(Shape) / fill(Shape)`` with stroke & dash attributes only but it does it very well !

Release history
===============

Latest release: https://github.com/bourgesl/marlin-renderer/releases/latest

| JDK | Default renderer | Available Marlin release |
| --- | --- | --- |
| Oracle JDK 6 - 8  | Ductus       | [Marlin-renderer 0.9.3](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_3) |
| Open JDK   6 - 8  | Pisces       | [Marlin-renderer 0.9.3](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_3) |
| Azul Zulu 8.20+   | Marlin 0.7.4 | [Marlin-renderer 0.9.3](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_3) |
| Jetbrains JDK 8   | Marlin 0.7.4 / 0.9.2 | Marlin 0.9.2 integrated in may 2018 |
| Oracle / Open JDK 9      | Marlin 0.7.4 | [Marlin-renderer 0.9.2 for JDK9+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_2_jdk9) |
| Oracle / Open JDK 10     | Marlin 0.8.2 | [Marlin-renderer 0.9.2 for JDK9+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_2_jdk9) |
| Oracle / Open JDK 11       | Marlin 0.9.1 | [Marlin-renderer 0.9.2 for JDK9+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_2_jdk9)

For JavaFX, see the [Marlin-FX](https://github.com/bourgesl/marlin-fx) project

News
====
* june 2018: MarlinFX 0.9.2 integrated in OpenJFX 11
* april 2018: Marlin 0.9.1 integrated in OpenJDK 11
* dec 2017: Marlin & MarlinFX 0.8.2 integrated in OpenJDK & OpenJFX 10
* nov 2017: Marlin rocks on [Geoserver benchmarks](https://gmf-test.sig.cloud.camptocamp.net/ms_perfs/): see geoserver (including Marlin 0.8.2) vs geoserver-jai results: it boosts geoserver to achieve MapServer performance !!
* My personal point of view after javaone: it is your turn to contribute to OpenJDK & OpenJFX: see [openjdk thread](http://mail.openjdk.java.net/pipermail/openjfx-dev/2017-October/020900.html)
* 4 oct 2017: JavaOne talk slides: [Marlin, a JDK9 Success Story: Vector Graphics on Steroids for Java 2D and JavaFX](https://github.com/bourgesl/bourgesl.github.io/raw/master/javaone2017/slides/javaone-marlin-talk.pdf)
* may 2017: Marlin & MarlinFX 0.7.5 integrated in OpenJFX10 (enabled by default)
* feb 2017: Blog post comparing Oracle ductus vs OpenJDK Marlin renderers (latency) [Performance Rendered Visual](https://www.azul.com/performance-rendered-visual/)
* nov 2016: MarlinFX 0.7.5 integrated in OpenJFX9
* jul 2016: Marlin integrated in [Jetbrains OpenJDK8 build](https://github.com/JetBrains/jdk8u) and running in IntelliJ IDEA 2016.3
* feb 2016: FOSDEM talk slides [Marlin renderer, a successful fork and join the OpenJDK 9 project](https://bourgesl.github.io/fosdem-2016/slides/fosdem-2016-Marlin.pdf)
* dec 2015: Marlin 0.7.4 integrated in OpenJDK9


Build status
============
Continuous Integration by Travis CI (build + unit & integration tests):
   * Branch unsafe-dev (jdk6-8): 
<img src="https://travis-ci.org/bourgesl/marlin-renderer.svg?branch=unsafe-dev" alt="build status"/>
   * Branch use_Unsafe (jdk6-8): 
<img src="https://travis-ci.org/bourgesl/marlin-renderer.svg?branch=use_Unsafe" alt="build status"/>
   * Branch jdk (jdk9+): 
<img src="https://travis-ci.org/bourgesl/marlin-renderer.svg?branch=openjdk" alt="build status"/>


License
=======

As marlin is a fork from OpenJDK 8 pisces, its license is the OpenJDK's license = GPL2 + ClassPath exception:

GNU General Public License, version 2,
with the Classpath Exception

The GNU General Public License (GPL)

Version 2, June 1991

See License.md


Performance
===========

See the [Benchmarks](https://github.com/bourgesl/marlin-renderer/wiki/Benchmarks)


Documentation
=============
Documentation on how to use and tune the Marlin renderer is available in the [wiki]( https://github.com/bourgesl/marlin-renderer/wiki)

Help is needed to improve the wiki & documentation !


Getting in touch
================

Users and developers interested in the Marlin-renderer are kindly invited to join the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group.


Contributing
============

Contributions are welcomed, get in touch with us on the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group and share your improvements via pull requests. 
Since we contribute this renderer into OpenJDK, we accept contributions from people that have signed the [Oracle Contribution Agreeement](http://www.oracle.com/technetwork/community/oca-486395.html) (very easy to do)


Support our work
================

To support my efforts on improving either Java2D or JavaFX rendering thanks to the Marlin & MarlinFX projects, please contribute to the gofundme campaign 'Marlin renderer 0.9 dev & FX port': https://www.gofundme.com/marlin-09 or for long-term support, you can become my patron: https://www.patreon.com/user?u=9339017

Please help if you appreciate the Marlin project:
   * share your benchmark & test results (quality ?)
   * improve test cases, quality & unit tests, submit bug reports
   * documentation should be improved & updated


Related projects
================

- [Mapbench](https://github.com/bourgesl/mapbench) provides testing & benchmarking tools based on real world map painted by the [GeoServer](http://geoserver.org/)
- [Marlin-FX](https://github.com/bourgesl/marlin-fx) provides the Marlin renderer port into JavaFX Prism (shape rasterizer)


Acknowledgments:
================
The Marlin renderer project benefits from the following tools:
* <a href="https://www.ej-technologies.com/products/jprofiler/overview.html">EJ-technologies Java Profiler <img src="https://www.ej-technologies.com/images/product_banners/jprofiler_medium.png" alt="JProfiler logo"></a> 

* <img src="https://www.yourkit.com/images/yklogo.png" alt="Yourkit"> supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>, innovative and intelligent tools for profiling Java and .NET applications.
