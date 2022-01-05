#!/bin/bash

source env.sh

# do force GC:
GC=true
FORK=1 # 0: no-fork
ITER=1

PROF="stack:lines=20;top=15;detailLine=true;excludePackages=false"
#PROF="gc"
#PROF="perfnorm"
#PROF="perfasm"
#PROF="pauses"

# Supported profilers:
#           cl: Classloader profiling via standard MBeans 
#         comp: JIT compiler profiling via standard MBeans 
#           gc: GC profiling via standard MBeans 
#          jfr: Java Flight Recorder profiler 
#       pauses: Pauses profiler 
#         perf: Linux perf Statistics 
#      perfasm: Linux perf + PrintAssembly Profiler 
#      perfc2c: Linux perf c2c profiler 
#     perfnorm: Linux perf statistics, normalized by operation count 
#   safepoints: Safepoints profiler 
#        stack: Simple and naive Java stack profiler 

# Unsupported profilers:
#        async: <none> 
# Unable to load async-profiler. Ensure asyncProfiler library is on LD_LIBRARY_PATH (Linux), DYLD_LIBRARY_PATH (Mac OS), or -Djava.library.path. Alternatively, point to
# explicit library location with -prof async:libPath=<path>.


OPTS="-p arraySize=$SIZES"

# Available formats: text, csv, scsv, json, latex
FORMAT=text

lscpu

sudo ~/cpu_fixed.sh

echo "JAVA:"
java -version

# define CPU core to use
# Note: use linux kernel GRUB_CMDLINE_LINUX="isolcpus=3" in /etc/default/grub
export CPU_CORE_IDS=


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

# stack profiler:
#JAVA_OPTS="$JAVA_OPTS -XX:-TieredCompilation -Djmh.stack.excludePackages=false"

# stack / perfasm options:
JAVA_OPTS="$JAVA_OPTS -XX:TieredStopAtLevel=4 -XX:-Inline"

JAVA_OPTS="$JAVA_OPTS -Djmh.stack.excludePackages=false --add-modules java.desktop --add-exports java.desktop/sun.java2d.marlin=ALL-UNNAMED --patch-module java.desktop=$DIR/../target/marlin-0.9.4.5-Unsafe-OpenJDK11.jar $MARLIN_OPTS"

echo "JAVA_OPTS: $JAVA_OPTS"


echo "Running JMH ..." 
# show help
#java -jar target/marlin-test-bench.jar -h

# show benchmarks & parameters
#java -jar target/marlin-test-bench.jar -lp

# single-threaded:
# -wi $WITER -w $WTIME -i $ITER -r $TIME -f $FORK 

#echo "CMD: java $JAVA_OPTS -jar $DIR/target/marlin-test-bench.jar -gc $GC -t 1 -f $FORK -i $ITER -prof $PROF $OPTS"
java $JAVA_OPTS -jar $DIR/target/marlin-test-bench.jar -gc $GC -t 1 -f $FORK -i $ITER -prof $PROF $OPTS 1> "prof-$PROF-cache-$SIZES.log" 2> "prof-$PROF-cache-$SIZES.err" &

PID=$!
echo "JAVA PID: $PID"
sudo cset shield --shield --threads --pid $PID

tail -f "prof-$PROF-cache-$SIZES.log"

