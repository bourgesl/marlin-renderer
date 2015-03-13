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
package sun.java2d.marlin;

/**
 * Fast Math routines
 */
final class FastMath implements MarlinConst {
    /* http://www.java-gaming.org/index.php?topic=24194.0 */

    private static final int BIG_ENOUGH_INT = 1024 * 1024 * 1024;    // 1e9 max
    private static final double BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT;

    private FastMath() {
        // utility class
    }

    /**
     * Faster Math.ceil implementation
     *
     * @param x float number
     * @return integer higher than given float number
     */
    public static int ceil(final float x) {
        if (useFastMath) {
            return BIG_ENOUGH_INT - (int) (BIG_ENOUGH_FLOOR - x);
            /*            
             final int ceil = BIG_ENOUGH_INT - (int) (BIG_ENOUGH_FLOOR - x);
             // check if this fast implementation is accurate:
             if (doChecks) {
             final int ceil_math = (int) Math.ceil(x);
             if (ceil != ceil_math) {
             logInfo("fast ceil(float) is wrong: " + ceil + " :: " + 
             ceil_math + " x: " + x);
             }
             }
             return ceil;
             */
        }
        return (int) Math.ceil(x);
    }

    /**
     * Faster Math.floor implementation
     *
     * @param x float number
     * @return integer higher than given float number
     */
    public static int floor(final float x) {
        if (useFastMath) {
            return (int) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
            /*            
             final int floor = (int) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;

             // check if this fast implementation is accurate:
             if (doChecks) {
             final int floor_math = (int) Math.floor(x);
             if (floor != floor_math) {
             logInfo("fast floor(float) is wrong: " + floor + " :: " + 
             floor_math + " x: " + x);
             }
             }
             return floor;
             */
        }
        return (int) Math.floor(x);
    }
}
