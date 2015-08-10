/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import static org.marlin.pisces.MarlinUtils.logInfo;

public final class ArrayCache implements MarlinConst {

    final static int BUCKETS = 4;
    final static int MIN_ARRAY_SIZE = 4096;
    final static int MAX_ARRAY_SIZE;
    final static int MASK_CLR_1 = ~1;
    // threshold to grow arrays only by (3/2) instead of 2
    final static int THRESHOLD_ARRAY_SIZE;
    final static int[] ARRAY_SIZES = new int[BUCKETS];
    // dirty byte array sizes
    final static int MIN_DIRTY_BYTE_ARRAY_SIZE = 32 * 2048; // 32px x 2048px
    final static int MAX_DIRTY_BYTE_ARRAY_SIZE;
    final static int[] DIRTY_BYTE_ARRAY_SIZES = new int[BUCKETS];
    // stats
    private static int resizeInt = 0;
    private static int resizeDirtyInt = 0;
    private static int resizeDirtyFloat = 0;
    private static int resizeDirtyByte = 0;
    private static int oversize = 0;

    static {
        // initialize buckets for int/float arrays
        int arraySize = MIN_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize <<= 2) {
            ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_ARRAY_SIZE = arraySize >> 2;

        /* initialize buckets for dirty byte arrays
         (large AA chunk = 32 x 2048 pixels) */
        arraySize = MIN_DIRTY_BYTE_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize <<= 1) {
            DIRTY_BYTE_ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("dirty arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_DIRTY_BYTE_ARRAY_SIZE = arraySize >> 1;

        // threshold to grow arrays only by (3/2) instead of 2
        THRESHOLD_ARRAY_SIZE = Math.max(2 * 1024 * 1024, MAX_ARRAY_SIZE);

        if (doStats || doMonitors) {
            logInfo("ArrayCache.BUCKETS        = " + BUCKETS);
            logInfo("ArrayCache.MIN_ARRAY_SIZE = " + MIN_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_ARRAY_SIZE = " + MAX_ARRAY_SIZE);
            logInfo("ArrayCache.ARRAY_SIZES = "
                    + Arrays.toString(ARRAY_SIZES));
            logInfo("ArrayCache.MIN_DIRTY_BYTE_ARRAY_SIZE = "
                    + MIN_DIRTY_BYTE_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_DIRTY_BYTE_ARRAY_SIZE = "
                    + MAX_DIRTY_BYTE_ARRAY_SIZE);
            logInfo("ArrayCache.ARRAY_SIZES = "
                    + Arrays.toString(DIRTY_BYTE_ARRAY_SIZES));
            logInfo("ArrayCache.THRESHOLD_ARRAY_SIZE = "
                    + THRESHOLD_ARRAY_SIZE);
        }
    }

    private ArrayCache() {
        // Utility class
    }

    static synchronized void incResizeInt() {
        resizeInt++;
    }

    static synchronized void incResizeDirtyInt() {
        resizeDirtyInt++;
    }

    static synchronized void incResizeDirtyFloat() {
        resizeDirtyFloat++;
    }

    static synchronized void incResizeDirtyByte() {
        resizeDirtyByte++;
    }

    static synchronized void incOversize() {
        oversize++;
    }

    static void dumpStats() {
        if (resizeInt != 0 || resizeDirtyInt != 0 || resizeDirtyFloat != 0
                || resizeDirtyByte != 0 || oversize != 0) {
            logInfo("ArrayCache: int resize: " + resizeInt
                    + " - dirty int resize: " + resizeDirtyInt
                    + " - dirty float resize: " + resizeDirtyFloat
                    + " - dirty byte resize: " + resizeDirtyByte
                    + " - oversize: " + oversize);
        }
    }

    // small methods used a lot (to be inlined / optimized by hotspot)

    static int getBucket(final int length) {
        for (int i = 0; i < ARRAY_SIZES.length; i++) {
            if (length <= ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    static int getBucketDirtyBytes(final int length) {
        for (int i = 0; i < DIRTY_BYTE_ARRAY_SIZES.length; i++) {
            if (length <= DIRTY_BYTE_ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return the new array size (~ x2)
     * @param curSize current used size
     * @return new array size
     */
    public static int getNewSize(final int curSize) {
        if (curSize > THRESHOLD_ARRAY_SIZE) {
            return ((curSize & MASK_CLR_1) * 3) >> 1;
        }
        // use next bucket giving array ~ x2:
        return (curSize & MASK_CLR_1) << 1;
    }
}
