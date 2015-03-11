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
final class MergeSort_ADV {

    private final static boolean DUMP_ARRAY_DATA = (false) && PiscesConst.doStats;

    public final static int INSERTION_SORT_THRESHOLD = 16;
    public final static boolean DISABLE_ASYMETRIC_MERGE = true;

    public final static boolean USE_MERGE_BIN_FIRST = false;
    public final static boolean USE_MERGE_TAIL_COPY = false;

    /**
     * Modified megacy merge sort:
     * Input arrays are in both auxX/auxY (sorted: 0 to insertionSortIndex) 
     *                     and x/y (unsorted: insertionSortIndex to toIndex)
     * Outputs are stored in x/y arrays
     */
    static void legacyMergeSortCustomNoCopy(final int[] x, final int[] y,
                                            final int[] auxX, final int[] auxY,
                                            final int fromIndex, final int toIndex,
                                            final int insertionSortIndex) {

        // Gather array data:
        if (DUMP_ARRAY_DATA) {
            // Copy presorted data from auxX/auxY to x/y:
            System.arraycopy(auxX, 0, x, 0, insertionSortIndex);
            RendererContext.stats.adc.addData(x, fromIndex, toIndex, insertionSortIndex);
        }

        // first part is already sorted in auxiliary storage (auxX/auxY)
        final int rightLen = toIndex - insertionSortIndex;

        // copy only second part to be sorted into auxX/auxY:
        System.arraycopy(x, insertionSortIndex, auxX, insertionSortIndex, rightLen);
        System.arraycopy(y, insertionSortIndex, auxY, insertionSortIndex, rightLen);

        // sort second part only using merge / insertion sort in auxiliary storage (auxX/auxY)
        mergeSort(x, auxX, y, auxY, insertionSortIndex, toIndex, -1);

        // final pass to merge both
        // Merge sorted parts (auxX/auxY) into x/y arrays
        if (DISABLE_ASYMETRIC_MERGE) {
            if ((insertionSortIndex == 0) || (auxX[insertionSortIndex - 1] <= auxX[insertionSortIndex])) {
                // no initial left part or both sublists (auxX, auxY) are already sorted:
                // copy back data into (x, y):
                System.arraycopy(auxX, fromIndex, x, fromIndex, toIndex);
                System.arraycopy(auxY, fromIndex, y, fromIndex, toIndex);
                return;
            }

            for (int i = fromIndex, p = fromIndex, q = insertionSortIndex; i < toIndex; i++) {
                if ((q >= toIndex) || (p < insertionSortIndex) && (auxX[p] <= auxX[q])) {
                    x[i] = auxX[p];
                    y[i] = auxY[p];
                    p++;
                } else {
                    x[i] = auxX[q];
                    y[i] = auxY[q];
                    q++;
                }
            }
            return;
        }

        // Merge only arrays:
        mergeSort(auxX, x, auxY, y, fromIndex, toIndex, insertionSortIndex);
    }

    /**
     * Src is the source array that starts at index 0 
     * Dest is the (possibly larger) array destination with a possible offset 
     * low is the index in dest to start sorting 
     * high is the end index in dest to end sorting 
     */
    private static void mergeSort(final int[] srcX, final int[] dstX,
                                  final int[] srcY, final int[] dstY,
                                  final int low, final int high,
                                  int mid) {

        int length = high - low;

        if (mid == -1) {
            /*
             * Tuning parameter: list size at or below which insertion sort will be used in preference to mergesort.
             * Benchmarks indicates 10 as the best threshold among [5,10,20]
             */
            if (length <= INSERTION_SORT_THRESHOLD) {
                // Insertion sort on smallest arrays
                // inside dest as both x == auxX initially

                for (int i = low + 1, j = low, x, y; i < high; j = i++) {
                    x = dstX[i];
                    y = dstY[i];

                    while (dstX[j] > x) {
                        // swap element
                        dstX[j + 1] = dstX[j];
                        dstY[j + 1] = dstY[j];
                        if (j-- == low) {
                            break;
                        }
                    }
                    dstX[j + 1] = x;
                    dstY[j + 1] = y;
                }

                return;
            }

            // Recursively sort halves of dest into src
            mid = (low + high) >>> 1;

            mergeSort(dstX, srcX, dstY, srcY, low, mid, -1);
            mergeSort(dstX, srcX, dstY, srcY, mid, high, -1);
        }

        // If arrays are already sorted, just copy from src to dest.  This is an
        // optimization that results in faster sorts for nearly ordered lists.
        if ((mid <= 0) || (srcX[mid - 1] <= srcX[mid])) {
            System.arraycopy(srcX, low, dstX, low, length);
            System.arraycopy(srcY, low, dstY, low, length);
            return;
        }
        // If sublist are inverted ie all(A) > all(B) do swap A and B to dst
        if (srcX[high - 1] <= srcX[low]) {
            final int left = mid - low;
            final int right = high - mid;
            final int off = (left != right) ? 1 : 0;
            // swap parts:
            System.arraycopy(srcX, low, dstX, mid + off, left);
            System.arraycopy(srcX, mid, dstX, low, right);
            System.arraycopy(srcY, low, dstY, mid + off, left);
            System.arraycopy(srcY, mid, dstY, low, right);
            return;
        }

        // Merge sorted halves (now in src) into dest
//        if (!USE_MERGE_BIN_FIRST || (high - mid) < 20) {
        if (true) {        
            for (int i = low, p = low, q = mid; i < high; i++) {
                if ((q >= high) || (p < mid) && (srcX[p] <= srcX[q])) {
                    dstX[i] = srcX[p];
                    dstY[i] = srcY[p];
                    p++;
                } else {
                    dstX[i] = srcX[q];
                    dstY[i] = srcY[q];
                    q++;
                }
            }
        } else {
            // Check which part is at beginning:
            if (srcX[low] <= srcX[mid]) {
                // left first
                int m;

                if (srcX[low] == srcX[mid]) {
                    m = low;
//                    System.out.println("left m = low");
                } else {
                    // Find insertion point in left part from first value in right part:
                    final int val = srcX[mid];
                    int l = low;
                    int h = mid - 1;

                    do {
                        m = (l + h) >>> 1;

                        if (srcX[m] < val) {
                            l = m + 1;
                        } else if (srcX[m] > val) {
                            h = m - 1;
                        } else {
                            break;
                        }
                    } while (l <= h);

                    length = (m - low);
//                        System.out.println("left m: " + length);

                    // Copy left part into dest (arraycopy ?):
                    System.arraycopy(srcX, low, dstX, low, length);
                    System.arraycopy(srcY, low, dstY, low, length);
                } // Merge:
                if (!USE_MERGE_TAIL_COPY) {
                    for (int i = m, p = m, q = mid; i < high; i++) {
                        if ((q >= high) || (p < mid) && (srcX[p] <= srcX[q])) {
                            dstX[i] = srcX[p];
                            dstY[i] = srcY[p];
                            p++;
                        } else {
                            dstX[i] = srcX[q];
                            dstY[i] = srcY[q];
                            q++;
                        }
                    }
                } else {
                    for (int i = m, p = m, q = mid; i < high; i++) {
                        if (srcX[p] <= srcX[q]) {
                            dstX[i] = srcX[p];
                            dstY[i] = srcY[p];
                            if (++p == mid) {
                                i++;
                                length = (high - q);
//                            System.out.println("left end(p): " + length);
                                // Optimize 1 or 2?
                                System.arraycopy(srcX, q, dstX, i, length);
                                System.arraycopy(srcY, q, dstY, i, length);
                                return;
                            }
                        } else {
                            dstX[i] = srcX[q];
                            dstY[i] = srcY[q];
                            if (++q == high) {
                                i++;
                                length = (mid - p);
//                            System.out.println("left end(q): " + length);
                                // Optimize 1 or 2?
                                System.arraycopy(srcX, p, dstX, i, length);
                                System.arraycopy(srcY, p, dstY, i, length);
                                return;
                            }
                        }
                    }
                }
            } else {
                // right first
                int m;

                // Find insertion point in right part from first value in left part:
                {
                    final int val = srcX[low];
                    int l = mid;
                    int h = high - 1;

                    do {
                        m = (l + h) >>> 1;

                        if (srcX[m] < val) {
                            l = m + 1;
                        } else if (srcX[m] > val) {
                            h = m - 1;
                        } else {
                            break;
                        }
                    } while (l <= h);
                }
                length = (m - mid);
//                    System.out.println("right m: " + length);

                // Copy right part into dest:
                System.arraycopy(srcX, mid, dstX, low, length);
                System.arraycopy(srcY, mid, dstY, low, length);

                // Merge:
                if (!USE_MERGE_TAIL_COPY) {
                    for (int i = (low + length), p = low, q = m; i < high; i++) {
                        if ((q >= high) || (p < mid) && (srcX[p] <= srcX[q])) {
                            dstX[i] = srcX[p];
                            dstY[i] = srcY[p];
                            p++;
                        } else {
                            dstX[i] = srcX[q];
                            dstY[i] = srcY[q];
                            q++;
                        }
                    }
                } else {
                    for (int i = (low + length), p = low, q = m; i < high; i++) {
                        if (srcX[p] <= srcX[q]) {
                            dstX[i] = srcX[p];
                            dstY[i] = srcY[p];
                            if (++p == mid) {
                                i++;
                                length = (high - q);
//                            System.out.println("left end(p): " + length);
                                // Optimize 1 or 2?
                                System.arraycopy(srcX, q, dstX, i, length);
                                System.arraycopy(srcY, q, dstY, i, length);
                                return;
                            }
                        } else {
                            dstX[i] = srcX[q];
                            dstY[i] = srcY[q];
                            if (++q == high) {
                                i++;
                                length = (mid - p);
//                            System.out.println("left end(q): " + length);
                                // Optimize 1 or 2?
                                System.arraycopy(srcX, p, dstX, i, length);
                                System.arraycopy(srcY, p, dstY, i, length);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private MergeSort_ADV() {
    }
}
