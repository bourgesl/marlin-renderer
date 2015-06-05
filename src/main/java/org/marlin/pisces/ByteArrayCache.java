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

import java.util.ArrayDeque;
import java.util.Arrays;
import static org.marlin.pisces.MarlinUtils.logException;
import static org.marlin.pisces.MarlinUtils.logInfo;

final class ByteArrayCache implements MarlinConst {

    private final int arraySize;
    private final ArrayDeque<byte[]> byteArrays;
    // stats
    private int getOp = 0;
    private int createOp = 0;
    private int returnOp = 0;

    void dumpStats() {
        if (getOp > 0) {
            logInfo("ByteArrayCache[" + arraySize + "]: get: " + getOp
                    + " created: " + createOp + " - returned: " + returnOp
                    + " :: cache size: " + byteArrays.size());
        }
    }

    ByteArrayCache(final int arraySize) {
        this.arraySize = arraySize;
        // small but enough: almost 1 cache line
        this.byteArrays = new ArrayDeque<byte[]>(6);
    }

    byte[] getArray() {
        if (doStats) {
            getOp++;
        }

        // use cache:
        final byte[] array = byteArrays.pollLast();
        if (array != null) {
            return array;
        }

        if (doStats) {
            createOp++;
        }

        return new byte[arraySize];
    }

    void putDirtyArray(final byte[] array, final int length) {
        if (doChecks && (length != arraySize)) {
            System.out.println("bad length = " + length);
            return;
        }
        if (doStats) {
            returnOp++;
        }

        // NO clean-up of array data = DIRTY ARRAY

        if (doCleanDirty) {
            // Force zero-fill dirty arrays:
            fill(array, 0, array.length, BYTE_0);
        }

        // fill cache:
        byteArrays.addLast(array);
    }

    void putArray(final byte[] array, final int length,
                  final int fromIndex, final int toIndex)
    {
        if (doChecks && (length != arraySize)) {
            System.out.println("bad length = " + length);
            return;
        }
        if (doStats) {
            returnOp++;
        }

        // clean-up array of dirty part[fromIndex; toIndex[
        fill(array, fromIndex, toIndex, BYTE_0);

        // fill cache:
        byteArrays.addLast(array);
    }

    static void fill(final byte[] array, final int fromIndex,
                     final int toIndex, final byte value)
    {
        // clear array data:
        /*
         * Arrays.fill is faster than System.arraycopy(empty array)
         * or Unsafe.setMemory(byte 0)
         */
        if (toIndex != 0) {
            Arrays.fill(array, fromIndex, toIndex, value);
        }

        if (doChecks) {
            check(array, 0, array.length, value);
        }
    }

    static void check(final byte[] array, final int fromIndex,
                      final int toIndex, final byte value)
    {
        if (doChecks) {
            // check zero on full array:
            for (int i = fromIndex; i < toIndex; i++) {
                if (array[i] != value) {
                    logException("Invalid array value at " + i + "\n"
                            + Arrays.toString(array), new Throwable());

                    // ensure array is correctly filled:
                    Arrays.fill(array, value);

                    return;
                }
            }
        }
    }
}