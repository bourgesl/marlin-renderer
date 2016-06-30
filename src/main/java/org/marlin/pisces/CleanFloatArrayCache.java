/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.marlin.pisces;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import static org.marlin.pisces.ArrayCache.*;
import static org.marlin.pisces.MarlinUtils.*;

final class CleanFloatArrayCache implements MarlinConst {

    private WeakReference<Bucket[]> refBuckets = null;
    private final int bucketCapacity;
    final CacheStats stats;

    CleanFloatArrayCache(final int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
        this.stats = (DO_STATS) ? new CacheStats("CleanFloatArrayCache") : null;
    }

    Bucket getCacheBucket(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return getBuckets()[bucket];
    }

    private Bucket[] getBuckets() {
        // resolve reference:
        Bucket[] buckets
            = (refBuckets != null) ? refBuckets.get() : null;

        // create a new buckets ?
        if (buckets == null) {
            buckets = new Bucket[BUCKETS];

            for (int i = 0; i < BUCKETS; i++) {
                buckets[i] = new Bucket(ARRAY_SIZES[i], bucketCapacity,
                        (DO_STATS) ? stats.bucketStats[i] : null);
            }

            // update weak reference:
            refBuckets = new WeakReference<Bucket[]>(buckets);
        }
        return buckets;
    }

    static final class Reference {

        // initial array reference (direct access)
        final float[] initial;
        private final CleanFloatArrayCache cache;

        Reference(final CleanFloatArrayCache cache,
                  final int initialSize)
        {
            this.cache = cache;
            this.initial = createArray(initialSize);

            if (DO_STATS) {
                cache.stats.totalInitial += initialSize;
            }
        }

        float[] getArray(final int length) {
            if (length <= MAX_ARRAY_SIZE) {
                return cache.getCacheBucket(length).getArray();
            }

            if (DO_STATS) {
                cache.stats.oversize++;
            }
            if (DO_LOG_OVERSIZE) {
                logInfo("CleanFloatArrayCache: getArray[oversize]: length=\t"
                        + length);
            }
            return createArray(length);
        }

        float[] widenArray(final float[] array, final int usedSize,
                          final int needSize)
        {
            final int length = array.length;
            if (DO_CHECKS && length >= needSize) {
                return array;
            }
            if (DO_STATS) {
                cache.stats.resize++;
            }

            // maybe change bucket:
            // ensure getNewSize() > newSize:
            final float[] res = getArray(getNewSize(usedSize, needSize));

            // use wrapper to ensure proper copy:
            System.arraycopy(array, 0, res, 0, usedSize); // copy only used elements

            // maybe return current array:
            putArray(array, 0, usedSize); // ensure array is cleared

            if (DO_LOG_WIDEN_ARRAY) {
                logInfo("CleanFloatArrayCache: widenArray[" + res.length
                        + "]: usedSize=\t" + usedSize + "\tlength=\t" + length
                        + "\tneeded length=\t" + needSize);
            }
            return res;
        }

        float[] putArray(final float[] array, final int fromIndex,
                        final int toIndex)
        {
            final int length = array.length;

            // ensure to never store initial arrays in cache:
            if ((array != initial) && (length <= MAX_ARRAY_SIZE)) {
                cache.getCacheBucket(length)
                    .putArray(array, length, fromIndex, toIndex);
            }
            return initial;
        }
    }

    static final class Bucket {

        private final int arraySize;
        private int tail = 0;
        private final float[][] arrays;
        private final BucketStats stats;

        Bucket(final int arraySize, final int capacity, final BucketStats stats)
        {
            this.arraySize = arraySize;
            this.stats = stats;
            this.arrays = new float[capacity][];
        }

        float[] getArray() {
            if (DO_STATS) {
                stats.getOp++;
            }

            // use cache:
            if (tail != 0) {
                final float[] array = arrays[--tail];
                arrays[tail] = null;
                return array;
            }

            if (DO_STATS) {
                stats.createOp++;
            }
            return createArray(arraySize);
        }

        void putArray(final float[] array, final int length,
                      final int fromIndex, final int toIndex)
        {
            if (length != arraySize) {
                if (DO_CHECKS) {
                    MarlinUtils.logInfo("CleanFloatArrayCache: bad length = "
                                        + length);
                }
                return;
            }
            if (DO_STATS) {
                stats.returnOp++;
            }

            // clean-up array of dirty part[fromIndex; toIndex[
            fill(array, fromIndex, toIndex, (float) 0);

            // fill cache:
            if (arrays.length > tail) {
                arrays[tail++] = array;

                if (DO_STATS) {
                    stats.updateMaxSize(tail);
                }
            } else {
                MarlinUtils.logInfo("CleanFloatArrayCache: array capacity exceeded !");
            }
        }
    }

    static float[] createArray(final int length) {
        return new float[length];
    }

    static void fill(final float[] array, final int fromIndex,
                     final int toIndex, final float value)
    {
        // clear array data:
        if (toIndex != 0) {
            Arrays.fill(array, fromIndex, toIndex, value);
        }

        if (DO_CHECKS) {
            check(array, fromIndex, toIndex, value);
        }
    }

    static void check(final float[] array, final int fromIndex,
                      final int toIndex, final float value)
    {
        if (DO_CHECKS) {
            // check zero on full array:
            for (int i = 0; i < array.length; i++) {
                if (array[i] != value) {
                    logException("Invalid value at: " + i + " = " + array[i]
                            + " from: " + fromIndex + " to: " + toIndex + "\n"
                            + Arrays.toString(array), new Throwable());

                    // ensure array is correctly filled:
                    Arrays.fill(array, value);

                    return;
                }
            }
        }
    }
}
