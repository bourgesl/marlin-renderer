package org.marlin.pisces;

/**
 * This class implements sorting algorithms by Vladimir Yaroslavskiy,
   such as Merging sort, the pair, nano and optimized insertion sorts,
 * counting sort and heap sort, invoked from the Dual-Pivot Quicksort.
 *
 * @author Vladimir Yaroslavskiy
 *
 * @version 2018.02.18
 * @since 10
 */
final class SortingAlgorithms2018Ext {

    private static final boolean DO_CHECKS = false;

    /**
     * Prevents instantiation.
     */
    private SortingAlgorithms2018Ext() {
    }

    /**
     * If the length of an array to be sorted is greater than this
     * constant, Merging sort is used in preference to Quicksort.
     */
    private static final int MERGING_SORT_THRESHOLD = 2048;

    /**
     * Calculates the maximum number of runs.
     *
     * @param length the array length
     * @return the maximum number of runs
     */
    public static int getMaxRunCount(int length) {
        return length > 2048000 ? 2000 : length >> 10 | 5;
    }

    /**
     * Tries to sort the specified range of the array by the Merging sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return true if the given array is finally sorted, false otherwise
     */
    static boolean mergingSort(int[] a, int[] b, int low, int high, int[] _auxA, int[] _auxB, int[] _run) {
        int length = high - low;

        if (length < MERGING_SORT_THRESHOLD) {
            return false;
        }

        /*
         * Index run[i] is the start of i-th run.
         * A run is a subsequence of elements
         * in ascending or descending order.
         */
        int max = getMaxRunCount(length);
        int[] run = (DO_CHECKS && (_run.length < max + 1)) ? new int[max + 1] : _run;
        int count = 0, last = low;
        run[0] = low;

        /*
         * Check if the array is highly structured.
         */
        for (int k = low + 1; k < high && count < max;) {
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse the run into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j];) {
                    int t = a[i];
                    a[i] = a[j];
                    a[j] = t;
                    t = b[i];
                    b[i] = b[j];
                    b[j] = t;
                }
            } else { // Sequence with equal elements
                for (int ak = a[k]; ++k < high && ak == a[k];);

                if (k < high) {
                    continue;
                }
            }

            if (count == 0 || a[last - 1] > a[last]) {
                ++count;
            }
            run[count] = (last = k);
        }

        if (count < max && count > 1) {
            /*
             * The array is highly structured, therefore merge all runs.
             */
            int[] auxA = (DO_CHECKS && (_auxA.length < a.length)) ? new int[length] : _auxA;
            int[] auxB = (DO_CHECKS && (_auxB.length < b.length)) ? new int[length] : _auxB;

            merge(a, auxA, b, auxB, true, low, run, 0, count);
        }
        return count < max;
    }

    /**
     * Merges the specified runs.
     *
     * @param srcA the source array
     * @param dstA the temporary buffer
     * @param src specifies the type of the target: source or buffer
     * @param offset the start index of the source, inclusive
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the target where runs are merged
     */
    private static int[] merge(int[] srcA, int[] dstA, int[] srcB, int[] dstB, boolean src,
                               int offset, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (src) {
                return srcA;
            }
            for (int i = run[hi], j = i - offset, low = run[lo]; i > low;
                    --j, --i, dstA[j] = srcA[i], dstB[j] = srcB[i]);
            return dstA;
        }
        int mi = (lo + hi) >>> 1;

        int[] a1, a2; // the left and the right halves to be merged

        a1 = merge(srcA, dstA, srcB, dstB, !src, offset, run, lo, mi);
        a2 = merge(srcA, dstA, srcB, dstB, !src, offset, run, mi, hi);

        return merge(
                a1 == srcA ? dstA : srcA,
                a1 == srcA ? dstB : srcB,
                a1 == srcA ? run[lo] - offset : run[lo],
                a1,
                a1 == srcA ? srcB : dstB,
                a1 == dstA ? run[lo] - offset : run[lo],
                a1 == dstA ? run[mi] - offset : run[mi],
                a2,
                a2 == srcA ? srcB : dstB,
                a2 == dstA ? run[mi] - offset : run[mi],
                a2 == dstA ? run[hi] - offset : run[hi]);
    }

    /**
     * Merges the sorted halves.
     *
     * @param dstA the destination where halves are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first half
     * @param i the start index of the first half, inclusive
     * @param hi the end index of the first half, exclusive
     * @param a2 the second half
     * @param j the start index of the second half, inclusive
     * @param hj the end index of the second half, exclusive
     * @return the merged halves
     */
    private static int[] merge(int[] dstA, int[] dstB, int k,
                               int[] a1, int[] b1, int i, int hi, int[] a2, int[] b2, int j, int hj) {

        while (true) {
            if (a1[i] < a2[j]) {
                dstA[k] = a1[i];
                dstB[k] = b1[i];
                k++;
                i++;
            } else {
                dstA[k] = a2[j];
                dstB[k] = b2[j];
                k++;
                j++;
            }

            if (i == hi) {
                while (j < hj) {
                    dstA[k] = a2[j];
                    dstB[k] = b2[j];
                    k++;
                    j++;
                }
                return dstA;
            }
            if (j == hj) {
                while (i < hi) {
                    dstA[k] = a1[i];
                    dstB[k] = b1[j];
                    k++;
                    i++;
                }
                return dstA;
            }
        }
    }

    /**
     * Sorts the specified range of the array by the nano insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void nanoInsertionSort(int[] a, int[] b, int low, int high) {
        /*
         * In the context of Quicksort, the elements from the left part
         * play the role of sentinels. Therefore expensive check of the
         * left range on each iteration can be skipped.
         */
        for (int k; ++low < high;) {
            int ak = a[k = low];
            int bk = b[k];

            while (ak < a[--k]) {
                a[k + 1] = a[k];
                b[k + 1] = b[k];
            }
            a[k + 1] = ak;
            b[k + 1] = bk;
        }
    }

    /**
     * Sorts the specified range of the array by the pair insertion sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    static void pairInsertionSort(int[] a, int[] b, int left, int right) {
        /*
         * Align the left boundary.
         */
        left -= (left ^ right) & 1;

        /*
         * Two elements are inserted at once on each iteration.
         * At first, we insert the greater element (a2) and then
         * insert the less element (a1), but from position where
         * the greater element was inserted. In the context of a
         * Dual-Pivot Quicksort, the elements from the left part
         * play the role of sentinels. Therefore expensive check
         * of the left range on each iteration can be skipped.
         */
        for (int k; ++left < right;) {
            int a1 = a[k = ++left];
            int b1 = b[k];

            if (a[k - 2] > a[k - 1]) {
                int a2 = a[--k];
                int b2 = b[k];

                if (a1 > a2) {
                    a2 = a1;
                    a1 = a[k];
                    b2 = b1;
                    b1 = b[k];
                }
                while (a2 < a[--k]) {
                    a[k + 2] = a[k];
                    b[k + 2] = b[k];
                }
                a[++k + 1] = a2;
                b[k + 1] = b2;
            }
            while (a1 < a[--k]) {
                a[k + 1] = a[k];
                b[k + 1] = b[k];
            }
            a[k + 1] = a1;
            b[k + 1] = b1;
        }
    }

    /**
     * Sorts the specified range of the array by the heap sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    static void heapSort(int[] a, int b[], int left, int right) {
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
     * Pushes specified element down during the heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param valueA the given element
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void pushDown(int[] a, int b[], int p, int valueA, int valueB, int left, int right) {
        for (int k;; a[p] = a[k], b[p] = b[k], p = k) {
            k = (p << 1) - left + 2; // the index of the right child

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
}
