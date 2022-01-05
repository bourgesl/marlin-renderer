#!/bin/bash

SIZES="262145,4194305" #,16777217"


# Test JDK18EA (shipilev builds):
#export JAVA_HOME=/home/bourgesl/apps/openjdk-jdk-linux-x86_64-server-release/jdk/
#export JAVA_HOME=/home/bourgesl/apps/openjdk-jdk-linux-x86_64-server-release/jdk-arraycopy/

# Test JDK17 (hsdis prof)
export JAVA_HOME=/home/bourgesl/apps/jdk-17/

echo "JAVA_HOME: $JAVA_HOME"

PATH=$JAVA_HOME/bin/:$PATH
export PATH


