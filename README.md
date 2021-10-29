Marlin-renderer
===============

Marlin is an open source (GPL2 + CP) Java2D ``RenderingEngine`` optimized for performance (improved memory usage (~ no GC) and footprint, better multi-threading) and better visual quality based on openjdk's pisces implementation.
It handles shape rendering (``Graphics2D draw(Shape) / fill(Shape)`` with stroke & dash attributes only but it does it very well !

Release history
===============

Latest release: https://github.com/bourgesl/marlin-renderer/releases/latest

| JDK | Default renderer | Available Marlin release |
| --- | --- | --- |
| Open JDK 6 - 7    | Pisces       | [Marlin-renderer 0.9.4.3](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_3) |
| Oracle JDK 6 - 7  | Ductus       | [Marlin-renderer 0.9.4.3](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_3) |
| Open JDK 8        | Pisces       | [Marlin-renderer 0.9.4.5 for JDK8](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5) |
| Oracle JDK 8      | Ductus       | [Marlin-renderer 0.9.4.5 for JDK8](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5) |
| Azul Zulu 8.20+   | Marlin 0.7.4 / 0.9.1.1 | Marlin 0.9.1.1 integrated in 2018.12, [Marlin-renderer 0.9.4.5 for JDK8](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5) |
| Jetbrains JDK 8   | Marlin 0.7.4 / 0.9.3 | Marlin 0.9.3 integrated in 2018.09, [Marlin-renderer 0.9.4.5 for JDK8](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5) |
| Open JDK 9        | Marlin 0.7.4 | [Marlin-renderer 0.9.4.2 for JDK9+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_2_jdk9) |
| Open JDK 10       | Marlin 0.8.2 | [Marlin-renderer 0.9.4.2 for JDK9+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_2_jdk9) |
| Open JDK 11       | Marlin 0.9.1 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 11.0.2   | Marlin 0.9.1.1 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 12       | Marlin 0.9.1.1 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 13       | Marlin 0.9.1.1 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 14       | Marlin 0.9.1.3 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 15       | Marlin 0.9.1.3 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 16       | Marlin 0.9.1.3 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 17       | Marlin 0.9.1.4 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |
| Open JDK 18       | Marlin 0.9.1.4 | [Marlin-renderer 0.9.4.5 for JDK11+](https://github.com/bourgesl/marlin-renderer/releases/tag/v0_9_4_5_jdk11) |

For JavaFX, see the [Marlin-FX](https://github.com/bourgesl/marlin-fx) project

News
====
* jan 2021: Marlin 0.9.1.4 integrated in OpenJDK 17
* sep 2019: Marlin 0.9.1.3 integrated in OpenJDK 14
* aug 2019: Marlin 0.9.1.2 integrated in OpenJDK 14
* jul 2019: MarlinFX 0.9.3.1 integrated in OpenJFX 14, backported in OpenJFX 13
* oct 2018: MarlinFX 0.9.3 integrated in OpenJFX 12
* oct 2018: Marlin 0.9.1.1 backported to OpenJDK 11 updates (11.0.2)
* sep 2018: Marlin 0.9.1.1 integrated in OpenJDK 12
* jun 2018: MarlinFX 0.9.2 integrated in OpenJFX 11
* apr 2018: Marlin 0.9.1 integrated in OpenJDK 11
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
Continuous Integration base on Github Actions (build + unit & integration tests):
   * Branch unsafe-dev (jdk 8): 
[![CI](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml/badge.svg?branch=unsafe-dev)](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml)
   * Branch use_Unsafe (jdk 6 - 8): 
[![CI](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml/badge.svg?branch=use_Unsafe)](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml)
   * Branch jdk (jdk11+): [![CI](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml/badge.svg?branch=jdk)](https://github.com/bourgesl/marlin-renderer/actions/workflows/build.yml)


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

To support my efforts on improving either Java2D or JavaFX rendering thanks to the Marlin & MarlinFX projects, you can become my patron: https://www.patreon.com/user?u=9339017

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
