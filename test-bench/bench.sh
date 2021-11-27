#!/bin/bash

source env.sh

# do not force GC as setupTrial does cleanup() and sorters use pre-allocation
GC=false
FORK=5

OPTS="-p arraySize=$SIZES"

# Available formats: text, csv, scsv, json, latex
FORMAT=text

lscpu

sudo ~/cpu_fixed.sh

echo "JAVA:"
java -version

# define CPU core to use
# Note: use linux kernel GRUB_CMDLINE_LINUX="isolcpus=3" in /etc/default/grub
export CPU_CORE_IDS=3


# define Java options
# -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation
# -XX:-TieredCompilation
# -verbose:gc -Xloggc:gc.log
# -XX:+PrintGCApplicationStoppedTime
# -XX:-TieredCompilation to disable C1/C2 tiered compilation
# -XX:TieredStopAtLevel=1 to disable C2 ie only C1
JAVA_OPTS="-Xms1g -Xmx1g -XX:+UseParallelGC -XX:-BackgroundCompilation"
#JAVA_OPTS="-Xms1g -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000"
#JAVA_OPTS="-Xms1g -Xmx1g -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000"

#JAVA_OPTS="-Xms1g -Xmx1g -XX:+UseParallelGC -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler"


DIR=`pwd`

# Allow access to Marlin-renderer package:
#JAVA_OPTS="-verbose $JAVA_OPTS --add-modules java.desktop --add-reads java.desktop=jdk.unsupported,ALL-UNNAMED --add-exports java.desktop/sun.java2d.marlin=ALL-UNNAMED --patch-module java.desktop=$DIR/../target/marlin-0.9.4.5-Unsafe-OpenJDK11-no-rdr.jar -Dsun.java2d.renderer.log=true -Dsun.java2d.renderer.doStats=true"

MARLIN_OPTS=""
#MARLIN_OPTS="-Dsun.java2d.renderer.log=true -Dsun.java2d.renderer.doStats=true"

JAVA_OPTS="$JAVA_OPTS --add-modules java.desktop --add-exports java.desktop/sun.java2d.marlin=ALL-UNNAMED --patch-module java.desktop=$DIR/../target/marlin-0.9.4.5-Unsafe-OpenJDK11-no-rdr.jar $MARLIN_OPTS"

echo "JAVA_OPTS: $JAVA_OPTS"


echo "Running JMH ..." 
# show help
#java -jar target/marlin-test-bench.jar -h

# show benchmarks & parameters
#java -jar target/marlin-test-bench.jar -lp

# single-threaded:
# -wi $WITER -w $WTIME -i $ITER -r $TIME -f $FORK 

#echo "CMD: java $JAVA_OPTS -jar $DIR/target/marlin-test-bench.jar -gc $GC -t 1 $OPTS"
java $JAVA_OPTS -jar $DIR/target/marlin-test-bench.jar -gc $GC -t 1 $OPTS 1> "sort-$SIZES.log" 2> "sort-$SIZES.err"

