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
package sun.java2d.marlin;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import static sun.java2d.marlin.ArrayCache.*;
import static sun.java2d.marlin.MarlinUtils.logInfo;

final class DirtyByteArrayCache implements MarlinConst {

    private WeakReference<Bucket[]> refBuckets = null;
    private final int bucketCapacity;
    final CacheStats stats;

    DirtyByteArrayCache(final int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
        this.stats = (DO_STATS) ? new CacheStats("DirtyByteArrayCache") : null;
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

        // initial array reference (direct access):
        final byte[] initial;
        private final DirtyByteArrayCache cache;

        Reference(final DirtyByteArrayCache cache,
                  final int initialSize)
        {
            this.cache = cache;
            this.initial = createArray(initialSize);

            if (DO_STATS) {
                cache.stats.totalInitial += initialSize;
            }
        }

        byte[] getArray(final int length) {
            if (length <= MAX_ARRAY_SIZE) {
                return cache.getCacheBucket(length).getArray();
            }

            if (DO_STATS) {
                cache.stats.oversize++;
            }
            if (DO_LOG_OVERSIZE) {
                logInfo("DirtyByteArrayCache: getArray[oversize]: length=\t"
                        + length);
            }
            return createArray(length);
        }

        byte[] widenArray(final byte[] array, final int usedSize,
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
            final byte[] res = getArray(getNewSize(usedSize, needSize));

            // use wrapper to ensure proper copy:
            System.arraycopy(array, 0, res, 0, usedSize); // copy only used elements

            // maybe return current array:
            putArray(array);

            if (DO_LOG_WIDEN_ARRAY) {
                logInfo("DirtyByteArrayCache: widenArray[" + res.length
                        + "]: usedSize=\t" + usedSize + "\tlength=\t" + length
                        + "\tneeded length=\t" + needSize);
            }
            return res;
        }

        byte[] putArray(final byte[] array) {
            final int length = array.length;

            // ensure to never store initial arrays in cache:
            if ((array != initial) && (length <= MAX_ARRAY_SIZE)) {
                cache.getCacheBucket(length).putArray(array, length);
            }
            return initial;
        }
    }

    static final class Bucket {

        private final int arraySize;
        private int tail = 0;
        private final byte[][] arrays;
        private final BucketStats stats;

        Bucket(final int arraySize, final int capacity, final BucketStats stats)
        {
            this.arraySize = arraySize;
            this.stats = stats;
            this.arrays = new byte[capacity][];
        }

        byte[] getArray() {
            if (DO_STATS) {
                stats.getOp++;
            }

            // use cache:
            if (tail != 0) {
                final byte[] array = arrays[--tail];
                arrays[tail] = null;
                return array;
            }

            if (DO_STATS) {
                stats.createOp++;
            }
            return createArray(arraySize);
        }

        void putArray(final byte[] array, final int length)
        {
            if (length != arraySize) {
                if (DO_CHECKS) {
                    MarlinUtils.logInfo("DirtyByteArrayCache: bad length = "
                                        + length);
                }
                return;
            }
            if (DO_STATS) {
                stats.returnOp++;
            }

            // NO clean-up of array data = DIRTY ARRAY
            if (DO_CLEAN_DIRTY) {
                // Force zero-fill dirty arrays:
                Arrays.fill(array, 0, length, (byte)0);
            }

            // fill cache:
            if (arrays.length > tail) {
                arrays[tail++] = array;

                if (DO_STATS) {
                    stats.updateMaxSize(tail);
                }
            } else {
                MarlinUtils.logInfo("DirtyByteArrayCache: array capacity exceeded !");
            }
        }
    }

    static byte[] createArray(final int length) {
        // use JDK9 Unsafe.allocateUninitializedArray(class, length):
//	    return (byte[]) OffHeapArray.UNSAFE.allocateUninitializedArray(byte.class, length);
        return new byte[length];
    }
}
