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
package test;

/**
 * In-place QuickSort adapted from (OpenJDK 6) java.util.Array.sort(int[]) (ie sort1) to swap two arrays at the same time (x & y)
 */
final class QuickSort {

    /**
     * Sorts the specified sub-array of integers into ascending order.
     */
    static void sort1(final int[] x, final int[] y, final int off, final int len) {
        int t;
        // Insertion sort on smallest arrays
        if (len < 20) { // 7 in jdk8 or 20 as benchmark ?
            for (int i = off; i < len + off; i++) {
                for (int j = i; j > off && x[j - 1] > x[j]; j--) {
                    /* swap(x, x2, j, j - 1); */
                    t = x[j];
                    x[j] = x[j - 1];
                    x[j - 1] = t;
                    t = y[j];
                    y[j] = y[j - 1];
                    y[j - 1] = t;
                }
            }
            return;
        }

        // Choose a partition element, v
        int m = off + (len >> 1); // Small arrays, middle element
// condition always satisfied?
//        if (len > 7) {
        int l = off;
        int p = off + len - 1;
        if (len > 40) {
            // Big arrays, pseudomedian of 9
            final int s = len / 8;
            l = med3(x, l, l + s, l + 2 * s);
            m = med3(x, m - s, m, m + s);
            p = med3(x, p - 2 * s, p - s, p);
        }
        m = med3(x, l, m, p); // Mid-size, med of 3
//        }
        final int v = x[m];

        // Establish Invariant: v* (<v)* (>v)* v*
        int a = off, b = a, c = off + len - 1, d = c;
        while (true) {
            while (b <= c && x[b] <= v) {
                if (x[b] == v) {
                    /* swap(x, y, a++, b); */
                    t = x[a];
                    x[a] = x[b];
                    x[b] = t;
                    t = y[a];
                    y[a] = y[b];
                    y[b] = t;
                    a++;
                }
                b++;
            }
            while (c >= b && x[c] >= v) {
                if (x[c] == v) {
                    /* swap(x, y, c, d--); */
                    t = x[c];
                    x[c] = x[d];
                    x[d] = t;
                    t = y[c];
                    y[c] = y[d];
                    y[d] = t;
                    d--;
                }
                c--;
            }
            if (b > c) {
                break;
            }
            /* swap(x, y, b++, c--); */
            t = x[b];
            x[b] = x[c];
            x[c] = t;
            t = y[b];
            y[b] = y[c];
            y[c] = t;
            b++;
            c--;
        }

        // Swap partition elements back to middle
        final int n = off + len;
        int s = Math.min(a - off, b - a);

        /* vecswap(x, y, off, b - s, s); */
        for (int i = 0, j = off, k = b - s; i < s; i++, j++, k++) {
            /* swap(x, y, j, k); */
            t = x[j];
            x[j] = x[k];
            x[k] = t;
            t = y[j];
            y[j] = y[k];
            y[k] = t;
        }

        s = Math.min(d - c, n - d - 1);

        /* vecswap(x, y, b, n - s, s); */
        for (int i = 0, j = b, k = n - s; i < s; i++, j++, k++) {
            /* swap(x, y, j, k); */
            t = x[j];
            x[j] = x[k];
            x[k] = t;
            t = y[j];
            y[j] = y[k];
            y[k] = t;
        }

        // Recursively sort non-partition-elements
        if ((s = b - a) > 1) {
            sort1(x, y, off, s);
        }
        if ((s = d - c) > 1) {
            sort1(x, y, n - s, s);
        }
    }

    /**
     * Returns the index of the median of the three indexed integers.
     */
    private static int med3(final int[] x, final int a, final int b, final int c) {
        return (x[a] < x[b]
                ? (x[b] < x[c] ? b : x[a] < x[c] ? c : a)
                : (x[b] > x[c] ? b : x[a] > x[c] ? c : a));
    }
}
