#!/bin/sh
# Replicate changes from the ByteArrayCache class to the [Int/Float]ArrayCache classes

sed -e 's/(b\yte)[ ]*//g' -e 's/b\yte/int/g' -e 's/B\yte/Int/g' < B\yteArrayCache.java > IntArrayCache.java
sed -e 's/(b\yte)[ ]*/(float) /g' -e 's/b\yte/float/g' -e 's/B\yte/Float/g' < B\yteArrayCache.java > FloatArrayCache.java
