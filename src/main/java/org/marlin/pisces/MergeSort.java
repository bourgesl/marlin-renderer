/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * MergeSort adapted from (OpenJDK 8) java.util.Array.legacyMergeSort(Object[])
 * to swap two arrays at the same time (x & y)
 * and use external auxiliary storage for temporary arrays
 */

final class MergeSort {

    private static final boolean LOG = false;
    private static boolean LOG_XY = false;

    private static final boolean USE_DPQS = true;
    
    // insertion sort threshold
    private static final int INSERTION_SORT_THRESHOLD = 14;

    public static final boolean SORT_TIME = false;

    private static final boolean USE_BOTTOM_UP = false;

    public static final boolean USE_QSORT = false;
    // insertion sort threshold
    private static final int QSORT_ISORT_RUN = 16;

    private static final boolean USE_QSORTE = true;

    private static final boolean CHECK_SORTED = false;

    // TEST ONLY:
    private final static int[] run_TEST;

    static {
        System.out.println("USE_DPQS: " + USE_DPQS);
        /*        
        System.out.println("USE_BOTTOM_UP: " + USE_BOTTOM_UP);
        System.out.println("USE_QSORT: " + USE_QSORT);
        System.out.println("USE_QSORTE: " + USE_QSORTE);
        System.out.println("USE_INPLACE: " + USE_INPLACE);
         */
        System.out.println("CHECK_SORTED: " + CHECK_SORTED);

        // USE MAX RUN LENGTH:
        final int max = SortingAlgorithms2018Ext.getMaxRunCount(Integer.MAX_VALUE) + 1;
        System.out.println("run_TEST: " + max);
        run_TEST = new int[max];
    }

    /**
     * Modified merge sort:
     * Input arrays are in both auxX/auxY (sorted: 0 to insertionSortIndex)
     *                     and x/y (unsorted: insertionSortIndex to toIndex)
     * Outputs are stored in x/y arrays
     */
    static void mergeSortNoCopy(final int[] x, final int[] y,
                                final int[] auxX, final int[] auxY,
                                final int toIndex,
                                final int insertionSortIndex,
                                final boolean skipISort) {
        
        if ((toIndex > x.length) || (toIndex > y.length)
                || (toIndex > auxX.length) || (toIndex > auxY.length)) {
            // explicit check to avoid bound checks within hot loops (below):
            throw new ArrayIndexOutOfBoundsException("bad arguments: toIndex="
                    + toIndex);
        }
        if (USE_DPQS) {
//            final long start = (SORT_TIME) ? System.nanoTime() : 0;

            // TODO: store run_TEST in ctx
            if (skipISort) {
                DualPivotQuicksort2018Ext.sort(x, y, 0, toIndex, auxX, auxY, run_TEST);
                if (CHECK_SORTED) {
                    // validate:
                    checkRange(x, 0, toIndex);
                }
            } else {
                DualPivotQuicksort2018Ext.sort(auxX, auxY, insertionSortIndex, toIndex, x, y, run_TEST);
                if (CHECK_SORTED) {
                    // validate:
                    checkRange(auxX, insertionSortIndex, toIndex);
                }
            }
/*
            if (SORT_TIME) {
                System.out.println("DPQS[" + insertionSortIndex + " - " + toIndex + "] : "
                        + (1e-6d * (System.nanoTime() - start)) + " ms");
            }
*/            
            if (skipISort) {
                return;
            }
        } else {
            if (USE_QSORT) {
                final long start = (SORT_TIME) ? System.nanoTime() : 0;

                if (USE_QSORTE) {
                    qsorte(auxX, auxY, insertionSortIndex, toIndex - 1,
                            (insertionSortIndex + toIndex >> 1)); // pivot = value at middle key
                } else {
                    mQuickSort(auxX, auxY, insertionSortIndex, toIndex - 1,
                            auxX[toIndex - 1]); // last key
                }

                if (SORT_TIME) {
                    System.out.println("qSort[" + insertionSortIndex + " - " + toIndex + "] : "
                            + (1e-6d * (System.nanoTime() - start)) + " ms");
                }

                if (CHECK_SORTED) {
                    // validate:
                    checkRange(x, 0, toIndex);
                }
            } else {
                final long start = (SORT_TIME) ? System.nanoTime() : 0;

                // Original's Marlin merge sort:
                // sort second part only using merge / insertion sort
                // in auxiliary storage (auxX/auxY)
                if (USE_BOTTOM_UP) {
                    bottomUpMergesort(auxX, x, auxY, y, insertionSortIndex, toIndex);
                } else {
                    mergeSort(x, y, x, auxX, y, auxY, insertionSortIndex, toIndex);
                }

                if (SORT_TIME) {
                    System.out.println("mergeSort[" + insertionSortIndex + " - " + toIndex + "] : "
                            + (1e-6d * (System.nanoTime() - start)) + " ms");
                }

                if (false) {
                    // shuffle equal X corresponding edges (y):
                    final int[] a = (USE_BOTTOM_UP) ? x : auxX;
                    final int[] b = (USE_BOTTOM_UP) ? y : auxY;
                    int prev = -1;
                    for (int i = insertionSortIndex; i < toIndex - 1; i++) {
                        if (a[i] == a[i + 1]) {
                            if (prev == -1) {
                                prev = i;
                            }
                        } else {
                            if (prev != -1) {
                                final int n = i - prev;
                                if (n > 1) {

                                    System.out.println("shuffle: enter");
                                    System.out.println("a:    " + Arrays.toString(Arrays.copyOfRange(a, prev, i)));
                                    System.out.println("b:    " + Arrays.toString(Arrays.copyOfRange(b, prev, i)));

                                    for (int p = prev, q = i; p < q; p++, q--) {
                                        int t = a[p];
                                        a[p] = a[q];
                                        a[q] = t;
                                        t = b[p];
                                        b[p] = b[q];
                                        b[q] = t;
                                    }
                                    /*                                    
                                    // shuffle current range [prev; i -1] as equal X => Y
                                    for (int j = n; j > 1; j--) {
                                        final int s = prev + j - 1;
                                        final int d = prev + random.nextInt(j); 
                                        int t = a[s];
                                        a[s] = a[d];
                                        a[d] = t;
                                        t = b[s];
                                        b[s] = b[d];
                                        b[d] = t;
                                    }
                                     */
                                    System.out.println("shuffle: exit");
                                    System.out.println("a:    " + Arrays.toString(Arrays.copyOfRange(a, prev, i)));
                                    System.out.println("b:    " + Arrays.toString(Arrays.copyOfRange(b, prev, i)));

                                }
                            }
                            prev = -1;
                        }
                    }
                }
            }
        }
        // final pass to merge both
        // Merge sorted parts (auxX/auxY) into x/y arrays
        if ((insertionSortIndex == 0)
                || (auxX[insertionSortIndex - 1] <= auxX[insertionSortIndex])) {
            // 34 occurences
            // no initial left part or both sublists (auxX, auxY) are sorted:
            // copy back data into (x, y):
            System.arraycopy(auxX, 0, x, 0, toIndex);
            System.arraycopy(auxY, 0, y, 0, toIndex);
            return;
        }

        final long start = (SORT_TIME) ? System.nanoTime() : 0;

        for (int i = 0, p = 0, q = insertionSortIndex; i < toIndex; i++) {
            if ((q >= toIndex) || ((p < insertionSortIndex)
                    && (auxX[p] <= auxX[q]))) {
                x[i] = auxX[p];
                y[i] = auxY[p];
                p++;
            } else {
                x[i] = auxX[q];
                y[i] = auxY[q];
                q++;
            }
        }

        if (SORT_TIME) {
            System.out.println("merge[0 - " + toIndex + "] : "
                    + (1e-6d * (System.nanoTime() - start)) + " ms");
        }

        if (CHECK_SORTED) {
            // validate:
            checkRange(x, 0, toIndex);
        }
    }

    /*

    private static int minRunLen = 24;

    public static void mergesortCheckSorted(int[] A, int left, int right) {
        int n = right - left + 1;
        int[] B = new int[n];
        if (minRunLen != 1) {
            for (int len = minRunLen, i = left; i <= right; i += len) {
                Insertionsort.insertionsort(A, i, min(i + len - 1, right));
            }
        }
        for (int len = minRunLen; len < n; len *= 2) {
            for (int i = left; i <= right - len; i += len + len) {
                if (A[i + len - 1] > A[i + len]) {
                    mergeRuns(A, i, i + len, min(i + len + len - 1, right), B);
                }
            }
        }
    }
     */
    /**
     * Src is the source array that starts at index 0
     * Dest is the (possibly larger) array destination with a possible offset
     * low is the index in dest to start sorting
     * high is the end index in dest to end sorting
     */
    private static void mergeSort(final int[] refX, final int[] refY,
                                  final int[] srcX, final int[] dstX,
                                  final int[] srcY, final int[] dstY,
                                  final int low, final int high) {
        final int length = high - low;

        /*
         * Tuning parameter: list size at or below which insertion sort
         * will be used in preference to mergesort.
         */
        if (length <= INSERTION_SORT_THRESHOLD) {
            // Insertion sort on smallest arrays
            dstX[low] = refX[low];
            dstY[low] = refY[low];

            for (int i = low + 1, j = low, x, y; i < high; j = i++) {
                x = refX[i];
                y = refY[i];

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
        // note: use signed shift (not >>>) for performance
        // as indices are small enough to exceed Integer.MAX_VALUE
        final int mid = (low + high) >> 1;

        mergeSort(refX, refY, dstX, srcX, dstY, srcY, low, mid);
        mergeSort(refX, refY, dstX, srcX, dstY, srcY, mid, high);

        // If arrays are inverted ie all(A) > all(B) do swap A and B to dst
        if (srcX[high - 1] <= srcX[low]) {
            // 1561 occurences
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

        // If arrays are already sorted, just copy from src to dest.  This is an
        // optimization that results in faster sorts for nearly ordered lists.
        if (srcX[mid - 1] <= srcX[mid]) {
            // 14 occurences
            System.arraycopy(srcX, low, dstX, low, length);
            System.arraycopy(srcY, low, dstY, low, length);
            return;
        }

        // Merge sorted halves (now in src) into dest
        for (int i = low, p = low, q = mid; i < high; i++) {
            if ((q >= high) || ((p < mid) && (srcX[p] <= srcX[q]))) {
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

    private MergeSort() {
    }

    private static final int MIN_RUN_LEN = 16;

    private static void bottomUpMergesort(final int[] srcX, final int[] auxX,
                                          final int[] srcY, final int[] auxY,
                                          final int low, final int high) {

        for (int len = MIN_RUN_LEN, left = low, right; left < high; left += len) {
            if (false) {
                insertionsort(srcX, srcY, left, Math.min(left + len, high));
            } else {
                right = Math.min(left + len, high);
                for (int i = left + 1, j, x, y, curx = srcX[left]; i < right; ++i) {
                    x = srcX[i];

                    if (x < curx) {
                        y = srcY[i];

                        for (j = i - 1; x < srcX[j];) {
                            srcX[j + 1] = srcX[j];
                            srcY[j + 1] = srcY[j];
                            if (--j < left) {
                                break;
                            }
                        }
                        srcX[j + 1] = x;
                        srcY[j + 1] = y;
                    } else {
                        curx = x;
                    }
                }
            }
        }
        for (int len = MIN_RUN_LEN, maxLen = high - low; len < maxLen; len *= 2) {
            for (int left = low, mid, right, i, j; left < high - len; left += len + len) {
                mid = left + len;
                if (srcX[mid - 1] > srcX[mid]) {
                    right = Math.min(mid + len, high);
                    if (false) {
                        mergeRuns(srcX, auxX, srcY, auxY, left, mid - 1, right);
                    } else {
                        mid--;
                        for (i = mid + 1; i > left; --i) {
                            auxX[i - 1] = srcX[i - 1];
                            auxY[i - 1] = srcY[i - 1];
                        }
                        final int r = right - 1;
                        for (j = mid; j < r; ++j) {
                            auxX[r + mid - j] = srcX[j + 1];
                            auxY[r + mid - j] = srcY[j + 1];
                        }
                        for (int k = left; k <= r; ++k) {
                            if (auxX[j] < auxX[i]) {
                                srcX[k] = auxX[j];
                                srcY[k] = auxY[j];
                                j--;
                            } else {
                                srcX[k] = auxX[i];
                                srcY[k] = auxY[i];
                                i++;
                            }
                        }
                    }
                }
            }
        }
        if (CHECK_SORTED) {
            // validate:
            checkRange(srcX, low, high);
        }
    }

    /** Sort A[left..right] by straight-insertion sort */
    private static void insertionsort(final int[] srcX, final int[] srcY, final int low, final int high) {
        for (int i = low + 1, j, x, y, curx = srcX[low]; i < high; ++i) {
            x = srcX[i];

            if (x < curx) {
                y = srcY[i];

                for (j = i - 1; x < srcX[j];) {
                    srcX[j + 1] = srcX[j];
                    srcY[j + 1] = srcY[j];
                    if (--j < low) {
                        break;
                    }
                }
                srcX[j + 1] = x;
                srcY[j + 1] = y;
            } else {
                curx = x;
            }
        }
    }

    /**
     * Merges runs A[l..m-1] and A[m..r] in-place into A[l..r]
     * with Sedgewick's bitonic merge (Program 8.2 in Algorithms in C++)
     * using B as temporary storage.
     * B.length must be at least r+1.
     */
    private static void mergeRuns(final int[] srcX, final int[] auxX,
                                  final int[] srcY, final int[] auxY,
                                  final int low, final int middle, final int high) {
        int i, j;
        for (i = middle + 1; i > low; --i) {
            auxX[i - 1] = srcX[i - 1];
            auxY[i - 1] = srcY[i - 1];
        }
        final int r = high - 1;
        for (j = middle; j < r; ++j) {
            auxX[r + middle - j] = srcX[j + 1];
            auxY[r + middle - j] = srcY[j + 1];
        }
        for (int k = low; k <= r; ++k) {
            if (auxX[j] < auxX[i]) {
                srcX[k] = auxX[j];
                srcY[k] = auxY[j];
                j--;
            } else {
                srcX[k] = auxX[i];
                srcY[k] = auxY[i];
                i++;
            }
        }
    }


    /* From Mainwright 1987
    Quicksort algorithms with an early exit for sorted subfiles
     */
    private static void qsorte(final int[] srcX,
                               final int[] srcY,
                               final int m, final int n, int pivot_loc) {

        final int len = n - m;
        if (len > 0) {
            if (true && (len <= QSORT_ISORT_RUN)) {
                // Insertion sort on smallest arrays
                for (int i = m + 1, j, x, y, curx = srcX[m]; i <= n; i++) {
                    x = srcX[i];

                    if (x < curx) {
                        y = srcY[i];
                        j = i - 1;
                        for (;;) {
                            // swap element
                            srcX[j + 1] = srcX[j];
                            srcY[j + 1] = srcY[j];
                            if ((j-- == m) || (x >= srcX[j])) {
                                break;
                            }
                        }
                        srcX[j + 1] = x;
                        srcY[j + 1] = y;
                    } else {
                        curx = x;
                    }
                }
            } else {
                boolean flag = true;

                int pivot = srcX[pivot_loc];
                int i = m;
                int j = n;
                boolean lsorted = true, rsorted = true;
                int t;

                while (flag) {
                    while (srcX[i] < pivot) {
                        if (lsorted) {
                            if (i > m) {
                                if (srcX[i] < srcX[i - 1]) {
                                    lsorted = false;
                                }
                            }
                        }
                        i += 1;
                    }

                    while ((j >= m) && (srcX[j] >= pivot)) {
                        if (rsorted) {
                            if (j < n) {
                                if (srcX[j] > srcX[j + 1]) {
                                    rsorted = false;
                                }
                            }
                        }
                        j -= 1;
                    }

                    if (i < j) {
                        // swap elements i and j
                        t = srcX[i];
                        srcX[i] = srcX[j];
                        srcX[j] = t;

                        t = srcY[i];
                        srcY[i] = srcY[j];
                        srcY[j] = t;

                        if (i == pivot_loc) {
                            pivot_loc = j;
                        }

                        if (lsorted) {
                            if (i > m) {
                                if (srcX[i] < srcX[i - 1]) {
                                    lsorted = false;
                                }
                            }
                        }
                        if (rsorted) {
                            if (j < n) {
                                if (srcX[j] > srcX[j + 1]) {
                                    rsorted = false;
                                }
                            }
                        }
                    } else {
                        flag = false;
                    }
                } // while flag

                if (!rsorted) {
                    // swap elements i and pivot_loc
                    t = srcX[i];
                    srcX[i] = srcX[pivot_loc];
                    srcX[pivot_loc] = t;

                    t = srcY[i];
                    srcY[i] = srcY[pivot_loc];
                    srcY[pivot_loc] = t;

                    i += 1;
                }

                if (!lsorted) {
                    qsorte(srcX, srcY, m, j, (m + j) >> 1);
                }
                if (!rsorted) {
                    qsorte(srcX, srcY, i, n, (i + n) >> 1);
                }
            }
        }
    }

    /*
    From Wulfenia journal:
    Enhancing Quicksort algorithm using a dynamic pivot selection technique
     */
    private static void mQuickSort(final int[] srcX,
                                   final int[] srcY,
                                   final int low, final int high, final int pivot) {

        final int len = high - low;
        if (len > 0) {
            if (len <= QSORT_ISORT_RUN) {
                // Insertion sort on smallest arrays
                for (int i = low + 1, j, x, y, curx = srcX[low]; i <= high; i++) {
                    x = srcX[i];

                    if (x < curx) {
                        y = srcY[i];
                        j = i - 1;
                        for (;;) {
                            // swap element
                            srcX[j + 1] = srcX[j];
                            srcY[j + 1] = srcY[j];
                            if ((j-- == low) || (x >= srcX[j])) {
                                break;
                            }
                        }
                        srcX[j + 1] = x;
                        srcY[j + 1] = y;
                    } else {
                        curx = x;
                    }
                }
            } else {
                boolean sorted = true; // n
                int i = low;
                int j = high;

                int countLess = 0, countLarger = 0;
                long sumLess = 0L, sumLarger = 0L;
                int k = srcX[high];
                int t;

                while (i <= j) {
                    if (srcX[i] <= pivot) {
                        countLess++;
                        sumLess += srcX[i];

                        if (sorted && (k >= (pivot - srcX[i]))) {
                            // k increasing => elements are increasing in sorted order
                            k = (pivot - srcX[i]);
                        } else {
                            sorted = false;
                        }
                        i++;
                    } else {
                        countLarger++;
                        sumLarger += srcX[i];

                        // swap elements i and j
                        t = srcX[i];
                        srcX[i] = srcX[j];
                        srcX[j] = t;

                        t = srcY[i];
                        srcY[i] = srcY[j];
                        srcY[j] = t;
                        j--;
                    }
                }
                if (countLess != 0) {
                    if (!sorted) {
                        // subarray is not sorted
                        mQuickSort(srcX, srcY, low, i - 1, (int) (sumLess / countLess));
                    }
                }
                if (countLarger != 0) {
                    mQuickSort(srcX, srcY, i, high, (int) (sumLarger / countLarger));
                }
            }
        }
    }

    private static void checkRange(int[] x, int lo, int hi) {
        for (int i = lo + 1; i < hi; i++) {
            if (x[i - 1] > x[i]) {
                System.out.println("Bad sorted x [" + (i - 1) + "]" + Arrays.toString(Arrays.copyOf(x, hi)));
                return;
            }
        }
    }

    private static final StringBuilder sb = new StringBuilder(8192);

    public static String dump(int[] x, int[] y, int toIndex) {
        sb.setLength(0);
        sb.append('[');
        for (int i = 0; i < toIndex; i++) {
            if (i != 0) {
                sb.append(' ');
            }
            if (LOG_XY) {
                sb.append('{').append(x[i]).append(' ').append(y[i]).append('}');
            } else {
                sb.append(x[i]).append(' ');
            }
        }
        return sb.toString();
    }
}
