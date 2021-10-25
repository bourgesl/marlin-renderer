#!/bin/bash

#BOOTCLASSPATH="-Xbootclasspath/a:/home/marlin/mapbench/lib/openjdk8-pisces.jar"
#RDR="-Dsun.java2d.renderer=sun.java2d.pisces.PiscesRenderingEngine"

MARLIN_PREFIX="/home/marlin/mapbench/lib/marlin-0.9.4.3-Unsafe"
MARLIN_PREFIX="./target/marlin-1.0.0-EA-Unsafe"

BOOTCLASSPATH="-Xbootclasspath/a:$MARLIN_PREFIX.jar -Xbootclasspath/p:$MARLIN_PREFIX-sun-java2d.jar"
RDR="-Dsun.java2d.renderer=org.marlin.pisces.DMarlinRenderingEngine"

JAVA_OPTS="-verbose:gc -Xms2g -Xmx2g $RDR -Dsun.java2d.renderer.log=true"

echo "RDR: $RDR"
echo "BOOTCLASSPATH: $BOOTCLASSPATH"
echo "JAVA_OPTS: $JAVA_OPTS"

java $BOOTCLASSPATH $JAVA_OPTS -jar target/marlin-1.0.0-EA-Unsafe-test.jar
#  test.DrawCurveApplication

