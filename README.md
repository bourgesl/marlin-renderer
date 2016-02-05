Marlin-renderer
===============

Marlin is an open source (GPL2+CP) Java2D RenderingEngine optimized for performance (improved memory usage (less GC) and footprint, better multi-threading) based on openjdk's pisces implementation.

Latest release: https://github.com/bourgesl/marlin-renderer/releases/latest

News:
* My slides at FOSDEM 2016 about 'Marlin renderer, a successful fork and join the OpenJDK 9 project': [fosdem-2016-Marlin.pdf](https://bourgesl.github.io/fosdem-2016/slides/fosdem-2016-Marlin.pdf)


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

Contributions are welcomed, get in touch with us on the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group and share your improvements via pull requests. Since we aim, one day, to contribute this renderer back to OpenJDK, we will accept contributions only from people that have signed the [Oracle Contribution Agreeement](http://www.oracle.com/technetwork/community/oca-486395.html)


Related projects
===============

[Mapbench](https://github.com/bourgesl/mapbench) provides benchmarking tools based on real world map painted by the [GeoServer](http://geoserver.org/) WMS server
