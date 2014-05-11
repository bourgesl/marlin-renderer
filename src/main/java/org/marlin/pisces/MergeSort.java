/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * MergeSort adapted from (OpenJDK 8) java.util.Array.legacyMergeSort(Object[]) to swap two arrays at the same time (x &
 * y) and use external auxiliary storage for temporary arrays
 */
final class MergeSort {

    /**
     * Modified megacy merge sort:
     * Inputs and outputs are respectively in auxX/auxY and x/y arrays (no arraycopy)
     */
    static void legacyMergeSortCustomNoCopy(final int[] x, final int[] y,
                                            final int[] auxX, final int[] auxY,
                                            final int fromIndex, final int toIndex,
                                            final int insertionSortIndex) {

        // first part is already sorted in auxiliary storage (auxX/auxY)

        final int rightLen = toIndex - insertionSortIndex;

        // copy only second part to be sorted into auxX/auxY:
        System.arraycopy(x, insertionSortIndex, auxX, insertionSortIndex, rightLen);
        System.arraycopy(y, insertionSortIndex, auxY, insertionSortIndex, rightLen);

        // sort second part only using insertion sort in auxiliary storage (auxX/auxY)
        mergeSort(x, auxX, y, auxY, insertionSortIndex, toIndex);

        // final pass to merge both

        // TODO: use binarysearch when merging asymetric parts ie right part << left part 
        // to have less comparisons !

        // Merge sorted parts (auxX/auxY) into x/y arrays
        for (int i = fromIndex, p = fromIndex, q = insertionSortIndex; i < toIndex; i++) {
            if (q >= toIndex || p < insertionSortIndex && auxX[p] <= auxX[q]) {
                x[i] = auxX[p];
                y[i] = auxY[p];
                p++;
            } else {
                x[i] = auxX[q];
                y[i] = auxY[q];
                q++;
            }
        }
    }

    /**
     * Src is the source array that starts at index 0 
     * Dest is the (possibly larger) array destination with a possible offset 
     * low is the index in dest to start sorting 
     * high is the end index in dest to end sorting 
     */
    private static void mergeSort(final int[] srcX, int[] dstX,
                                  final int[] srcY, final int[] dstY,
                                  int low, int high) {

        final int length = high - low;
        int t;

        /*
         * Tuning parameter: list size at or below which insertion sort will be used in preference to mergesort.
         * Benchmarks indicates 10 as the best threshold among [5,10,20]
         */
        if (length <= 10) {
            // Insertion sort on smallest arrays
            for (int i = low; i < high; i++) {
                for (int j = i; j > low && dstX[j - 1] > dstX[j]; j--) {
                    /* swap(dstX, j, j - 1); */
                    t = dstX[j];
                    dstX[j] = dstX[j - 1];
                    dstX[j - 1] = t;
                    t = dstY[j];
                    dstY[j] = dstY[j - 1];
                    dstY[j - 1] = t;
                }
            }
            return;
        }

        // Recursively sort halves of dest into src
        final int mid = (low + high) >>> 1;
        mergeSort(dstX, srcX, dstY, srcY, low, mid);
        mergeSort(dstX, srcX, dstY, srcY, mid, high);

        // If list is already sorted, just copy from src to dest.  This is an
        // optimization that results in faster sorts for nearly ordered lists.
        if (srcX[mid - 1] <= srcX[mid]) {
            System.arraycopy(srcX, low, dstX, low, length);
            System.arraycopy(srcY, low, dstY, low, length);
            return;
        }

        // Merge sorted halves (now in src) into dest
        for (int i = low, p = low, q = mid; i < high; i++) {
            if (q >= high || p < mid && srcX[p] <= srcX[q]) {
                dstX[i] = srcX[p];
                dstY[i] = srcY[p];
                p++;
            } else {
                dstX[i] = srcX[q];
                dstY[i] = srcY[q];
                q++;
            }
        }
    }
}
