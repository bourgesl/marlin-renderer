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
package org.marlin.pisces.stats;

import java.io.Serializable;

/**
 *
 * @author bourgesl
 */
public class ArraySortData implements Serializable, Comparable<ArraySortData> {

    private static final long serialVersionUID = 1L;

    /* members */
    /** array to be sorted */
    int[] x;
    /** starting index to sort (inclusive) >= 0 */
    int fromIndex;
    /** ending index to sort (exclusive) <= array.length */
    int toIndex;
    /** optional index indicating the ending index of the sorted part (exclusive) */
    int sortedIndex;

    public ArraySortData(final int[] x, final int fromIndex, final int toIndex, final int sortedIndex) {
        this.x = x;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.sortedIndex = sortedIndex;
    }

    public int[] getX() {
        return x;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getToIndex() {
        return toIndex;
    }

    public int getSortedIndex() {
        return sortedIndex;
    }

    public int getUnsortedRatio() {
        return ((10 * (toIndex - sortedIndex)) / toIndex);
    }

    @Override
    public int compareTo(ArraySortData o) {
        int res = Integer.compare(getUnsortedRatio(), o.getUnsortedRatio());
        if (res == 0) {
            return Integer.compare(toIndex, o.toIndex);
        }
        return res;
    }


}
