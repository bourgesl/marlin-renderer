/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

import java.util.Arrays; // TODO
// import java.util.concurrent.CountedCompleter;
// import java.util.concurrent.RecursiveTask;

/**
 * This class implements powerful and fully optimized versions, both
 * sequential and parallel, of the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley and Josh Bloch. This algorithm
 * offers O(n log(n)) performance on all data sets, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * There are also additional algorithms such as parallel merge sort,
 * pair insertion sort, merging of runs, heap sort and counting sort
 * invoked from the Dual-Pivot Quicksort.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 * @author Doug Lea
 *
 * @version 2018.08.18
 *
 * @since 1.7 * 12
 */
public final class DualPivotQuicksort20181121Ext {

    private static final boolean LOG_ALLOC = false;

    /* 
    From OpenJDK12 source code
     */
    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort20181121Ext() {
    }

    /**
     * Max array size to use pair insertion sort.
     */
    private static final int MAX_PAIR_INSERTION_SORT_SIZE = 114;

    /**
     * Max array size to use heap sort for the leftmost part.
     */
    private static final int MAX_HEAP_SORT_SIZE = 69;

    /**
     * Min array size to try merging of runs.
     */
    private static final int MIN_TRY_MERGE_SIZE = 1 << 12;

    /**
     * Min size of the first run to continue with scanning.
     */
    private static final int MIN_FIRST_RUN_SIZE = 32;

    /**
     * Min factor for the first runs to continue scanning.
     */
    private static final int MIN_FIRST_RUNS_FACTOR = 6;

    /**
     * Max double recursive partitioning depth before using heap sort.
     */
    private static final int MAX_RECURSION_DEPTH = 64 << 1;

    /**
     * Sorts the specified range of the array.
     *
     * @param a the array to be sorted
     * @param b the permutation array to be handled
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    public static void sort(Sorter sorter, int[] a, int b[], int low, int high) {
        sort(sorter, a, b, 0, low, high);
    }

    /**
     * Sorts the specified array using the Dual-Pivot Quicksort and/or
     * other sorts in special-cases, possibly with parallel partitions.
     *
     * @param sorter parallel context
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that array is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(Sorter sorter, int[] a, int[] b, int bits, int low, int high) {
        while (true) {
            int end = high - 1, size = high - low;

            /*
             * Run pair insertion sort on small non-leftmost parts.
             */
            if (size < MAX_PAIR_INSERTION_SORT_SIZE && (bits & 1) > 0) {
                end -= 3 * (size >> 4 << 2);
                pairInsertionSort(a, b, low, end, size);
                return;
            }

            /*
             * Switch to heap sort on the leftmost part or // TODO
             * if the execution time is becoming quadratic.
             */
            if (size < MAX_HEAP_SORT_SIZE) {
                insertionSort(a, b, low, high);
                return;
            }

            /*
             * Check if the whole array or non-left parts
             * are nearly sorted and then merge runs.
             */
            if ((bits == 0 || (bits & 1) > 0) && size > MIN_TRY_MERGE_SIZE
                    && tryMergeRuns(sorter, a, b, low, size)) {
                return;
            }

            /*
             * Switch to heap sort on the leftmost part or // TODO
             * if the execution time is becoming quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, b, low, end);
                return;
            }

            /*
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 3) * 3 + 3;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in place by the combination
             * of 5-element sorting network and insertion sort.
             */
            if (a[e5] < a[e3]) {
                int t = a[e5];
                a[e5] = a[e3];
                a[e3] = t;
                t = b[e5];
                b[e5] = b[e3];
                b[e3] = t;
            }
            if (a[e4] < a[e2]) {
                int t = a[e4];
                a[e4] = a[e2];
                a[e2] = t;
                t = b[e4];
                b[e4] = b[e2];
                b[e2] = t;
            }
            if (a[e5] < a[e4]) {
                int t = a[e5];
                a[e5] = a[e4];
                a[e4] = t;
                t = b[e5];
                b[e5] = b[e4];
                b[e4] = t;
            }
            if (a[e3] < a[e2]) {
                int t = a[e3];
                a[e3] = a[e2];
                a[e2] = t;
                t = b[e3];
                b[e3] = b[e2];
                b[e2] = t;
            }
            if (a[e4] < a[e3]) {
                int t = a[e4];
                a[e4] = a[e3];
                a[e3] = t;
                t = b[e4];
                b[e4] = b[e3];
                b[e3] = t;
            }

            if (a[e1] > a[e2]) {
                final int ta = a[e1];
                a[e1] = a[e2];
                a[e2] = ta;
                final int tb = b[e1];
                b[e1] = b[e2];
                b[e2] = tb;
                if (ta > a[e3]) {
                    a[e2] = a[e3];
                    a[e3] = ta;
                    b[e2] = b[e3];
                    b[e3] = tb;
                    if (ta > a[e4]) {
                        a[e3] = a[e4];
                        a[e4] = ta;
                        b[e3] = b[e4];
                        b[e4] = tb;
                        if (ta > a[e5]) {
                            a[e4] = a[e5];
                            a[e5] = ta;
                            b[e4] = b[e5];
                            b[e5] = tb;
                        }
                    }
                }
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with 2 pivots in case of different elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                final int pivotA1 = a[e1];
                final int pivotA2 = a[e5];
                final int pivotB1 = b[e1];
                final int pivotB2 = b[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];
                b[e1] = b[lower];
                b[e5] = b[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivotA1);
                while (a[--upper] > pivotA2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *   left part                 central part          right part
                 * +------------------------------------------------------------+
                 * |  < pivot1  |   ?   |  pivot1 <= && <= pivot2  |  > pivot2  |
                 * +------------------------------------------------------------+
                 *             ^       ^                            ^
                 *             |       |                            |
                 *           lower     k                          upper
                 *
                 * Invariants:
                 *
                 *              all in (low, lower] < pivot1
                 *    pivot1 <= all in (k, upper)  <= pivot2
                 *              all in [upper, end) > pivot2
                 *
                 * Pointer k is the last index of ?-part
                 */
                for (int unused = --lower, k = ++upper; --k > lower;) {
                    int ak = a[k];
                    int bk = b[k];

                    if (ak < pivotA1) { // Move a[k] to the left side
                        while (k > lower) {
                            if (a[++lower] >= pivotA1) {
                                if (a[lower] > pivotA2) {
                                    a[k] = a[--upper];
                                    a[upper] = a[lower];
                                    b[k] = b[upper];
                                    b[upper] = b[lower];
                                } else {
                                    a[k] = a[lower];
                                    b[k] = b[lower];
                                }
                                a[lower] = ak;
                                b[lower] = bk;
                                break;
                            }
                        }
                    } else if (ak > pivotA2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                        b[k] = b[upper];
                        b[upper] = bk;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower];
                a[lower] = pivotA1;
                a[end] = a[upper];
                a[upper] = pivotA2;

                b[low] = b[lower];
                b[lower] = pivotB1;
                b[end] = b[upper];
                b[upper] = pivotB2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                sort(sorter, a, b, bits | 1, lower + 1, upper);
                sort(sorter, a, b, bits | 1, upper + 1, high);

            } else { // Use single pivot in case of many equal elements

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                final int pivotA = a[e3];
                final int pivotB = b[e3];

                /*
                 * The first element to be sorted is moved to
                 * the location formerly occupied by the pivot.
                 * When partitioning is completed, the pivot is
                 * swapped back into its final position, and
                 * excluded from the next subsequent sorting.
                 */
                a[e3] = a[lower];
                b[e3] = b[lower];

                /*
                 * Traditional 3-way (Dutch National Flag) partitioning
                 *
                 *   left part                 central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Invariants:
                 *
                 *   all in (low, lower] < pivot
                 *   all in (k, upper)  == pivot
                 *   all in [upper, end] > pivot
                 *
                 * Pointer k is the last index of ?-part
                 */
                if (true) {
                    // Use special loop to handle properly A/B arrays (Vladimir fix):
                    for (int k = ++upper; --k > lower;) {
                        final int ak = a[k];

                        if (ak != pivotA) {
                            final int bk = b[k];

                            if (ak < pivotA) {
                                // Move a[k] to the left side
                                while (a[++lower] < pivotA);
                                /*
                                // LBO: restored range check:
                                if (lower > k) {
                                    lower = k;
                                    break;
                                }
                                 */
                                if (a[lower] > pivotA) {
                                    a[k] = a[--upper];
                                    a[upper] = a[lower];
                                    b[k] = b[upper];
                                    b[upper] = b[lower];
                                } else {
                                    a[k] = a[lower];
                                    b[k] = b[lower];
                                }
                                a[lower] = ak;
                                b[lower] = bk;
                            } else {
                                // ak > pivot - Move a[k] to the right side
                                a[k] = a[--upper];
                                a[upper] = ak;
                                b[k] = b[upper];
                                b[upper] = bk;
                            }
                        }
                    }

                } else {
                    for (int k = ++upper; --k > lower;) {
                        int ak = a[k];

                        if (ak != pivotA) {
                            a[k] = pivotA;

                            if (ak < pivotA) { // Move a[k] to the left side
                                while (a[++lower] < pivotA);

                                if (a[lower] > pivotA) {
                                    a[--upper] = a[lower];
                                }
                                a[lower] = ak;
                            } else { // ak > pivot - Move a[k] to the right side
                                a[--upper] = ak;
                            }
                        }
                    }
                }
                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower];
                a[lower] = pivotA;
                b[low] = b[lower];
                b[lower] = pivotB;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(sorter, a, b, bits | 1, upper, high);
            }
            high = lower; // Iterate along the left part
        }
    }

    // todo javadoc, todo byte
    private static void insertionSort(int[] a, int b[], int low, int high) {
        for (int i, k = low; ++k < high;) {
            int ak = a[i = k];
            int bk = b[k];

            if (ak < a[low]) {
                while (--i >= low) {
                    a[i + 1] = a[i];
                    b[i + 1] = b[i];
                }
            } else {
                while (ak < a[--i]) {
                    a[i + 1] = a[i];
                    b[i + 1] = b[i];
                }
            }
            a[i + 1] = ak;
            b[i + 1] = bk;
        }
    }

    /**
     * Sorts the specified range of the array by pair insertion sort.
     *
     * In the context of Quicksort, the pivot element between given
     * parts plays the role of sentinel. Therefore, expensive check
     * of the left range on each iteration can be skipped unless it
     * is the leftmost call. For initial array up to threshold, use
     * plain insertion sort. For remainder, insert two elements per
     * iteration, first, the greater element then the lesser, but
     * from position where the greater element was inserted.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param end the index of the last element for classic insertion sort
     * @param size the array size
     */
    private static void pairInsertionSort(int[] a, int[] b, int left, int end, int size) {
        int last = left + size;

        /*
         * Start with classic insertion sort on tiny part.
         */
        for (int k; left < end;) {
            int ak = a[k = ++left];
            int bk = b[k];

            while (ak < a[--k]) {
                a[k + 1] = a[k];
                b[k + 1] = b[k];
            }
            a[k + 1] = ak;
            b[k + 1] = bk;
        }

        /*
         * Continue with pair insertion sort on remain part.
         */
        for (int k; ++left < last;) {
            int a1 = a[k = left], a2 = a[++left];
            int b1 = b[k], b2 = b[left];

            if (a1 > a2) {

                while (a1 < a[--k]) {
                    a[k + 2] = a[k];
                    b[k + 2] = b[k];
                }
                a[++k + 1] = a1;
                b[k + 1] = b1;

                while (a2 < a[--k]) {
                    a[k + 1] = a[k];
                    b[k + 1] = b[k];
                }
                a[k + 1] = a2;
                b[k + 1] = b2;

            } else if (a1 < a[k - 1]) {

                while (a2 < a[--k]) {
                    a[k + 2] = a[k];
                    b[k + 2] = b[k];
                }
                a[++k + 1] = a2;
                b[k + 1] = b2;

                while (a1 < a[--k]) {
                    a[k + 1] = a[k];
                    b[k + 1] = b[k];
                }
                a[k + 1] = a1;
                b[k + 1] = b1;
            }
        }
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void heapSort(int[] a, int b[], int left, int right) {
        for (int k = (left + 1 + right) >>> 1; k > left;) {
            pushDown(a, b, --k, a[k], b[k], left, right);
        }
        for (int k = right; k > left; --k) {
            int maxA = a[left];
            int maxB = b[left];
            pushDown(a, b, left, a[k], b[k], left, k);
            a[k] = maxA;
            b[k] = maxB;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param valueA the given element
     * @param left the index of the first element, inclusive, of the range
     * @param right the index of the last element, inclusive, of the range
     */
    private static void pushDown(int[] a, int b[], int p, int valueA, int valueB, int left, int right) {
        for (int k;; a[p] = a[k], b[p] = b[k], p = k) {
            k = (p << 1) - left + 2; // Index of the right child

            if (k > right || a[k - 1] > a[k]) {
                --k;
            }
            if (k > right || a[k] <= valueA) {
                a[p] = valueA;
                b[p] = valueB;
                return;
            }
        }
    }

    /**
     * Calculates the max number of runs.
     *
     * @param size the array size
     * @return the max number of runs
     */
    public static int getMaxRunCount(int size) {
        return size > 2048000 ? 2000 : size >> 10 | 5;
    }

    /**
     * Tries to sort the specified range of the array.
     *
     * @param sorter parallel context
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param size the array size
     * @return true if finally sorted, false otherwise
     */
    private static boolean tryMergeRuns(Sorter sorter, int[] a, int[] b, int low, int size) {
        /*
         * The run array is constructed only if initial runs are
         * long enough to continue, run[i] then holds start index
         * of the i-th sequence of elements in non-descending order.
         */
        int[] run = null;
        int high = low + size;
        int count = 1, last = low;
        int max = getMaxRunCount(size);

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1; k < high && count < max;) {

            /*
             * Find the end index of the current run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j];) {
                    int t = a[i];
                    a[i] = a[j];
                    a[j] = t;
                    t = b[i];
                    b[i] = b[j];
                    b[j] = t;
                }
            } else { // Identify equal elements
                for (int ak = a[k]; ++k < high && ak == a[k];);

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check special cases.
             */
            if (sorter.runInit || run == null) {
                sorter.runInit = false; // LBO

                if (k == high) {

                    /*
                     * The array is monotonous sequence,
                     * and therefore already sorted.
                     */
                    return true;
                }

                if (k - low < MIN_FIRST_RUN_SIZE) {

                    /*
                     * The first run is too small
                     * to proceed with scanning.
                     */
                    return false;
                }

//                run = new int[INITIAL_RUN_CAPACITY];
                run = sorter.run; // LBO: prealloc
                run[0] = low;

            } else if (a[last - 1] > a[last]) {

                if (count > (k - low) >> MIN_FIRST_RUNS_FACTOR) {

                    /*
                     * The first runs are not long
                     * enough to continue scanning.
                     */
                    return false;
                }

                if (++count == run.length) {
                    run = Arrays.copyOf(run, count << 1);
                }
            }
            run[count] = (last = k);
        }

        /*
         * Check if array is highly structured and then merge runs.
         */
        if (count < max && count > 1) {
            int[] auxA = sorter.auxA;
            int[] auxB = sorter.auxB;
            int offset = low;

            // LBO: prealloc
            if (auxA.length < size || auxB.length < size) {
                if (LOG_ALLOC) {
                    MarlinUtils.logInfo("alloc auxA/auxB: " + size);
                }
                auxA = new int[size];
                auxB = new int[size];
            }
            mergeRuns(a, auxA, b, auxB, offset, 1, run, 0, count);
        }
        return count < max;
    }

    /**
     * Merges the specified runs.
     *
     * @param srcA the source array
     * @param dstA the temporary buffer used in merging
     * @param offset the start index in the source, inclusive
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static int[] mergeRuns(int[] srcA, int[] dstA, int[] srcB, int[] dstB, int offset,
                                   int aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return srcA;
            }
            for (int i = run[hi], j = i - offset, low = run[lo]; i > low;
                    --j, --i, dstA[j] = srcA[i], dstB[j] = srcB[i]);
            return dstA;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge the left and the right parts.
         */
        int[] a1, a2;
        a1 = mergeRuns(srcA, dstA, srcB, dstB, offset, -aim, run, lo, mi);
        a2 = mergeRuns(srcA, dstA, srcB, dstB, offset, 0, run, mi, hi);

        int[] b1, b2;
        b1 = a1 == srcA ? srcB : dstB;
        b2 = a2 == srcA ? srcB : dstB;

        int[] resA = a1 == srcA ? dstA : srcA;
        int[] resB = a1 == srcA ? dstB : srcB;

        int k = a1 == srcA ? run[lo] - offset : run[lo];
        int lo1 = a1 == dstA ? run[lo] - offset : run[lo];
        int hi1 = a1 == dstA ? run[mi] - offset : run[mi];
        int lo2 = a2 == dstA ? run[mi] - offset : run[mi];
        int hi2 = a2 == dstA ? run[hi] - offset : run[hi];

        mergeParts(resA, resB, k, a1, b1, lo1, hi1, a2, b2, lo2, hi2);

        return resA;
    }

    /**
     * Merges the sorted parts.
     *
     * @param dstA the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first part
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(int[] dstA, int[] dstB, int k,
                                   int[] a1, int[] b1, int lo1, int hi1, int[] a2, int[] b2, int lo2, int hi2) {
// ...
        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            if (a1[lo1] < a2[lo2]) {
                dstA[k] = a1[lo1];
                dstB[k] = b1[lo1];
                k++;
                lo1++;
            } else {
                dstA[k] = a2[lo2];
                dstB[k] = b2[lo2];
                k++;
                lo2++;
            }
        }
        if (dstA != a1 || k < lo1) {
            while (lo1 < hi1) {
                dstA[k] = a1[lo1];
                dstB[k] = b1[lo1];
                k++;
                lo1++;
            }
        }
        if (dstA != a2 || k < lo2) {
            while (lo2 < hi2) {
                dstA[k] = a2[lo2];
                dstB[k] = b2[lo2];
                k++;
                lo2++;
            }
        }
    }

    public static final class Sorter {

        final int[] run;
        int[] auxA;
        int[] auxB;
        boolean runInit;

        Sorter() {
            // preallocate max runs:
            final int max = getMaxRunCount(Integer.MAX_VALUE) + 1;
            if (LOG_ALLOC) {
                MarlinUtils.logInfo("alloc run: " + max);
            }
            run = new int[max];
        }

        public void initBuffers(final int length, final int[] a, final int[] b) {
            auxA = a;
            if (auxA.length < length) {
                if (LOG_ALLOC) {
                    MarlinUtils.logInfo("alloc auxA: " + length);
                }
                auxA = new int[length];
            }
            auxB = b;
            if (auxB.length < length) {
                if (LOG_ALLOC) {
                    MarlinUtils.logInfo("alloc auxB: " + length);
                }
                auxB = new int[length];
            }
            runInit = true;
        }
    }
}
