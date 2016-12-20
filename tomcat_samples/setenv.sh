#!/bin/sh

# Define your Java / JVM:
source ~/test-jdk8.sh
export JRE_HOME=$JAVA_HOME

# Define your path to marlin library:
MARLIN_PATH=/home/bourgesl/libs/marlin/mapbench/lib

# Define optional Marlin tuning options:
USE_TL=false
SIZE=2048
#SIZE=8192

export CATALINA_OPTS="-Xbootclasspath/a:$MARLIN_PATH/marlin-0.7.5-Unsafe.jar -Dsun.java2d.renderer.useThreadLocal=$USE_TL -Dsun.java2d.renderer.pixelsize=$SIZE -Dsun.java2d.renderer=org.marlin.pisces.MarlinRenderingEngine"

# Display java version
java -version

echo "CATALINA_OPTS='$CATALINA_OPTS'"

