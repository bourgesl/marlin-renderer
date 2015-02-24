/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static sun.java2d.marlin.MarlinUtils.logInfo;

/**
 *
 */
public final class ArrayCache implements MarlinConst {

    final static int BUCKETS = 4;
    final static int BUCKET_GROW_BITS = 2;
    final static int BUCKET_GROW = 1 << BUCKET_GROW_BITS;
    final static int BUCKET_DIRTY_GROW_BITS = 1;
    final static int BUCKET_DIRTY_GROW = 1 << BUCKET_DIRTY_GROW_BITS;
    final static int MIN_ARRAY_SIZE = 4096; // avoid too many resize (arrayCopy)
    /** threshold to grow arrays by x4 or x2 */
    final static int THRESHOLD_ARRAY_SIZE = 32 * 1024;
    /* array sizes */
    final static int MAX_ARRAY_SIZE;
    final static int[] ARRAY_SIZES = new int[BUCKETS];
    /* dirty array sizes */
    final static int MAX_DIRTY_ARRAY_SIZE;
    final static int[] DIRTY_ARRAY_SIZES = new int[BUCKETS];
    /* stats */
    static int resizeInt = 0;
    static int resizeFloat = 0;
    static int resizeDirty = 0;
    static int oversize = 0;

    static {
        /* initialize buckets for int/float arrays */
        int arraySize = MIN_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize *= BUCKET_GROW) {
            ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_ARRAY_SIZE = arraySize / BUCKET_GROW;

        /* initialize buckets for dirty int arrays (large AA chunk = 32 x pixels) */
        arraySize = BUCKET_DIRTY_GROW * MarlinCache.TILE_SIZE * 1024;  /* TODO: adjust max size */

        for (int i = 0; i < BUCKETS; i++, arraySize *= BUCKET_DIRTY_GROW) {
            DIRTY_ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("dirty arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_DIRTY_ARRAY_SIZE = arraySize / BUCKET_DIRTY_GROW;

        if (doStats || doMonitors) {
            logInfo("ArrayCache.BUCKETS        = " + BUCKETS);
            logInfo("ArrayCache.MIN_ARRAY_SIZE = " + MIN_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_ARRAY_SIZE = " + MAX_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_DIRTY_ARRAY_SIZE = " + MAX_DIRTY_ARRAY_SIZE);
        }
    }

    private ArrayCache() {
        // Utility class
    }

    static void dumpStats() {
        if (resizeInt != 0 || resizeFloat != 0 || resizeDirty != 0) {
            logInfo("ArrayCache: int resize: " + resizeInt
                    + " - float resize: " + resizeFloat
                    + " - dirty resize: " + resizeDirty
                    + " - oversize: " + oversize);
        }
    }

    /* TODO: use shifts to find bucket as fast as possible (no condition) */
    /* small methods used a lot (to be inlined / optimized by hotspot) */
    static int getBucket(final int length) {
        // Use size = (length / 2) * 2 => rounded to power of two
        // then switch == ? (unroll loops ?)
//        return length / MIN_ARRAY_SIZE
        for (int i = 0; i < BUCKETS; i++) {
            if (length <= ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }
    /* small methods used a lot (to be inlined / optimized by hotspot) */

    static int getBucketDirty(final int length) {
        // Use size = (length / 2) * 2 => rounded to power of two
        // then switch == ? (unroll loops ?)
//        return length / MIN_ARRAY_SIZE
        for (int i = 0; i < BUCKETS; i++) {
            if (length <= DIRTY_ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return the corrected  size by the growing factor
     * @param newSize needed size
     * @return corrected size
     */
    public static int getNewSize(final int newSize) {
        if (newSize < THRESHOLD_ARRAY_SIZE) {
            return BUCKET_GROW * newSize;
        }
        return 2 * newSize;
    }
}
