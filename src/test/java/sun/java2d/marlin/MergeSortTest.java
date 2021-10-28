/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.marlin.pisces.MergeSort.mergeSortNoCopy;
import org.marlin.pisces.stats.ArraySortData;
import org.marlin.pisces.stats.ArraySortDataCollection;

/**
 * Test for MergeSort
 */
public class MergeSortTest {

    private final static String TEST_PATH = "/home/marlin/branches/marlin-renderer-unsafe/"; // + "src/test/resources/"

    final static int N = 10;
    final static boolean TRACE = false;
    final static boolean TEST_HIGH_UNSORTED = true;

    public static void main(String[] args) {
        /*
         int[] srcX = new int[]{3, 4, 5, 11, 7};
         int[] srcY = new int[]{5, 6, 7, 12, 11};
         int[] auxX = new int[]{3, 4, 5, 11, 7};
         int[] auxY = new int[]{5, 6, 7, 12, 11};
         legacyMergeSortCustomNoCopy(srcX, srcY, auxX, auxY, 0, srcX.length, 3);
         */
        final ArraySortDataCollection adc = ArraySortDataCollection.load(
                TEST_PATH + "ArraySortDataCollection.ser"
        );

        final ArraySortData[] datas = adc.getData().toArray(new ArraySortData[0]);

        if (TEST_HIGH_UNSORTED) {
            Arrays.sort(datas);
        }

        final int size = datas.length;

        System.out.println("ArraySortData size = " + size);

        int maxLen = 0;

        for (int i = 0; i < size; i++) {
            ArraySortData d = datas[i];

            if (d.getToIndex() > maxLen) {
                maxLen = d.getToIndex();
            }

            if (TRACE) {
                System.out.println("len= " + d.getToIndex() + " sorted= " + d.getSortedIndex()
                        + " unsorted= " + d.getUnsortedRatio());
            }
        }

        System.out.println("maxLen= " + maxLen);

        // Show most unsorted:
        int POS = 0;
        if (TEST_HIGH_UNSORTED) {
            for (int i = size - 1; i >= 0; i--) {
                ArraySortData d = datas[i];

                System.out.println("len= " + d.getToIndex() + " sorted= " + d.getSortedIndex()
                        + " unsorted= " + d.getUnsortedRatio());

                if (d.getUnsortedRatio() < 50) {
                    POS = i + 1;
                    break;
                }
            }
        }

        // Sort arrays:
        final int[] x = new int[maxLen];
        final int[] y = new int[maxLen];
        final int[] auxX = new int[maxLen];
        final int[] auxY = new int[maxLen];

        System.out.println("INSERTION_SORT_THRESHOLD: " + MergeSort.INSERTION_SORT_THRESHOLD);

        System.out.println("Check sorting all: " + size);
        checkSort(datas, x, y, auxX, auxY);

        if (TEST_HIGH_UNSORTED) {
            // Most unsorted ratio:
            System.out.println("Sort most unsorted: " + (size - POS));
            test(POS, 20000, datas, x, y, auxX, auxY);
        } else {
            // ALL datas:
            System.out.println("Sort all: " + size);
            test(0, 1000, datas, x, y, auxX, auxY);
        }
    }

    private static void test(final int POS, final int JOBS,
                             final ArraySortData[] datas,
                             final int[] x, final int[] y,
                             final int[] auxX, final int[] auxY) {

        final int size = datas.length;

        int toIndex;
        int sortedIndex;

        long s, total;

        int sum = 0;
        long min = Long.MAX_VALUE, max = 0l, acc = 0l;

        for (int n = 0; n < N; n++) {
            final long start = System.nanoTime();
            total = 0l;
            sum = 0;

            for (int j = 0; j < JOBS; j++) {
                if (TRACE) {
                    System.out.println("PASS: " + j + " / " + JOBS);
                }
                for (int i = POS; i < size; i++) {
                    final ArraySortData d = datas[i];
                    if (TRACE) {
                        System.out.println("---");
                        System.out.println("len= " + d.getToIndex() + " sorted= " + d.getSortedIndex()
                                + " unsorted= " + d.getUnsortedRatio());
                    }

                    toIndex = d.getToIndex();
                    sortedIndex = d.getSortedIndex();

                    if (false) {
                        // copy data into x:
                        System.arraycopy(d.getX(), 0, x, sortedIndex, (toIndex - sortedIndex));
                        // copy data into auxX[0, sortedIndex]:
                        System.arraycopy(d.getX(), 0, auxX, 0, sortedIndex);
                        if (TRACE) {
                            System.out.println("sorting data:\n" + Arrays.toString(Arrays.copyOf(auxX, toIndex)));
                        }
                    } else {
                        // copy data into x:
                        System.arraycopy(d.getX(), 0, x, 0, toIndex);
                        // copy x into auxX:
                        System.arraycopy(x, 0, auxX, 0, sortedIndex);
                        if (TRACE) {
                            System.out.println("sorting data:\n" + Arrays.toString(Arrays.copyOf(x, toIndex)));
                        }
                    }

                    // START BENCHMARK
                    s = System.nanoTime();

                    mergeSortNoCopy(x, y, auxX, auxY, toIndex, sortedIndex, false, null, false);
                    // always consume test results:
                    sum += x[0] + x[toIndex - 1];

                    total += (System.nanoTime() - s);

                    // END BENCHMARK
                    if (TRACE) {
                        System.out.println("sorted data:\n" + Arrays.toString(Arrays.copyOf(x, toIndex)));
                    }
                }
            }

            System.out.println("test duration: " + (1e-6 * (System.nanoTime() - start))
                    + " ms - total: " + (1e-6 * (total))
                    + " ms. - sum: " + sum);

            acc += total;
            if (min > total) {
                min = total;
            }
            if (max < total) {
                max = total;
            }
        }

        System.out.println("test stats: acc: " + (1e-6 * acc) + " ms - min: " + (1e-6 * min)
                + " ms. - max: " + (1e-6 * max) + " ms. - avg: " + (1e-6 * acc) / N);
    }

    private static void checkSort(final ArraySortData[] datas,
                                  final int[] x, final int[] y,
                                  final int[] auxX, final int[] auxY) {

        final int size = datas.length;

        int toIndex;
        int sortedIndex;

        final long start = System.nanoTime();

        for (int i = 0; i < size; i++) {
            final ArraySortData d = datas[i];
            if (TRACE) {
                System.out.println("---");
                System.out.println("len= " + d.getToIndex() + " sorted= " + d.getSortedIndex()
                        + " unsorted= " + d.getUnsortedRatio());
            }

            toIndex = d.getToIndex();
            sortedIndex = d.getSortedIndex();

            if (false) {
                // copy data into x:
                System.arraycopy(d.getX(), 0, x, sortedIndex, (toIndex - sortedIndex));
                // copy data into auxX[0, sortedIndex]:
                System.arraycopy(d.getX(), 0, auxX, 0, sortedIndex);
                if (TRACE) {
                    System.out.println("sorting data:\n" + Arrays.toString(Arrays.copyOf(auxX, toIndex)));
                }
            } else {
                // copy data into x:
                System.arraycopy(d.getX(), 0, x, 0, toIndex);
                // copy x into auxX:
                System.arraycopy(x, 0, auxX, 0, sortedIndex);
                if (TRACE) {
                    System.out.println("sorting data:\n" + Arrays.toString(Arrays.copyOf(x, toIndex)));
                }
            }

            // sort:
            mergeSortNoCopy(x, y, auxX, auxY, toIndex, sortedIndex, false, null, false);

            if (TRACE) {
                System.out.println("sorted data:\n" + Arrays.toString(Arrays.copyOf(x, toIndex)));
            }
            // check items are well sorted in [0, toIndex]:
            for (int j = 0 + 1; j < toIndex; j++) {
                if (x[j - 1] > x[j]) {
                    // Bad order:
                    System.out.println("sorting data:\n" + Arrays.toString(Arrays.copyOf(d.getX(), toIndex)));
                    System.out.println("sorted data:\n" + Arrays.toString(Arrays.copyOf(x, toIndex)));
                    throw new IllegalStateException("array[" + i + "]: bad order at index: " + j);
                }
            }
        }

        System.out.println("duration: " + (1e-6 * (System.nanoTime() - start)) + " ms.");
    }
}