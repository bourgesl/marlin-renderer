Marlin-renderer
===============

Marlin is an open source (GPL2+CP) Java2D RenderingEngine optimized for performance (improved memory usage (less GC) and footprint, better multi-threading) based on openjdk's pisces implementation.

Latest release: https://github.com/bourgesl/marlin-renderer/releases/latest

News:
* My personal point of view after javaone: it is your turn to contribute to OpenJDK & OpenJFX: see [openjdk thread](http://mail.openjdk.java.net/pipermail/openjfx-dev/2017-October/020900.html)
* 4 oct 2017: JavaOne talk slides: [Marlin, a JDK9 Success Story: Vector Graphics on Steroids for Java 2D and JavaFX](https://github.com/bourgesl/bourgesl.github.io/raw/master/javaone2017/slides/javaone-marlin-talk.pdf)
* may 2017: Marlin & MarlinFX 0.7.5 integrated in OpenJFX10 (enabled by default)
* nov 2016: MarlinFX integrated in OpenJFX9
* jul 2016: Marlin integrated in [Jetbrains OpenJDK8 build](https://github.com/JetBrains/jdk8u) and running in IntelliJ IDEA 2016.3
* feb 2016: My slides at FOSDEM 2016 about 'Marlin renderer, a successful fork and join the OpenJDK 9 project': [fosdem-2016-Marlin.pdf](https://bourgesl.github.io/fosdem-2016/slides/fosdem-2016-Marlin.pdf)
* dec 2015: Marlin integrated in OpenJDK9


Build status
============
Continuous integration by Travis CI (build + tests):

   * Branch use_Unsafe: 
<img src="https://travis-ci.org/bourgesl/marlin-renderer.svg?branch=use_Unsafe" alt="build status"/>


License
=======

As marlin is a fork from OpenJDK 8 pisces, its license is the OpenJDK's license = GPL2+CP:

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
Documentation on how to use and tune the renderer is available in the [wiki]( https://github.com/bourgesl/marlin-renderer/wiki)


Getting in touch
================

Users and developers interested in the Marlin-renderer are kindly invited to join the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group.


Contributing
============

Contributions are welcomed, get in touch with us on the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group and share your improvements via pull requests. 
Since we contribute this renderer into OpenJDK, we accept contributions only from people that have signed the [Oracle Contribution Agreeement](http://www.oracle.com/technetwork/community/oca-486395.html)


Support our work
================

To support our efforts on improving either Java2D or JavaFX rendering thanks to the Marlin & MarlinFX projects, I launched a gofundme campaign: https://www.gofundme.com/javaone-2017-travel-costs

Please contribute if you appreciate the Marlin renderer to send me a gift in return ...


Related projects
===============

[Mapbench](https://github.com/bourgesl/mapbench) provides benchmarking tools based on real world map painted by the [GeoServer](http://geoserver.org/) WMS server
