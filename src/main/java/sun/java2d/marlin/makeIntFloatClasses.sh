#!/bin/sh
# Replicate changes from the ByteArrayCache classes to the [Int/Float]ArrayCache classes

for file in *ByteArrayCache.java
do
	echo Source File: $file
	intfile=`echo $file | sed -e 's/Byte/Int/g'`
	floatfile=`echo $file | sed -e 's/Byte/Float/g'`
	echo Int File: $intfile
	sed -e 's/(byte)[ ]*//g' -e 's/byte/int/g' -e 's/Byte/Int/g' < $file > $intfile
	echo Float File: $floatfile
	sed -e 's/(byte)[ ]*/(float) /g' -e 's/byte/float/g' -e 's/Byte/Float/g' < $file > $floatfile
done

