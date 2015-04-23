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

import sun.misc.FloatConsts;

/**
 * Faster Math ceil / floor routines derived from StrictMath
 */
final class FloatMath implements MarlinConst {

    /* http://www.java-gaming.org/index.php?topic=24194.0 */

    private static final int BIG_ENOUGH_INT = 1024 * 1024 * 1024;    // 1e9 max
    private static final double BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT;

    static final boolean USE_HACK = true && useFastMath;

    static final boolean CHECK = false;
    static final boolean CHECK_OVERFLOW = false;

    private FloatMath() {
        // utility class
    }

    /**
     * Faster Math.ceil implementation
     *
     * @param a a value.
     * @return the smallest (closest to negative infinity) integer value
     * that is greater than or equal to the argument and is equal to a
     * mathematical integer.
     */
    public static int ceil(final float a) {
        if (USE_HACK) {
            return BIG_ENOUGH_INT - (int) (BIG_ENOUGH_FLOOR - a);
        }
        if (useFastMath) {
            if (CHECK) {
                final int ceil = ceil_f(a);
                final int ceil_math = (int) Math.ceil(a);
                if (ceil != ceil_math) {
                    MarlinUtils.logInfo("ceil(float) is wrong: " + ceil + " :: "
                    + ceil_math + " x: " + a);
                }
                return ceil_math;
            }
            return ceil_f(a);
        }
        return (int) Math.ceil(a);
    }

    /**
     * Faster Math.floor implementation
     *
     * @param a a value.
     * @return the largest (closest to positive infinity) floating-point value
     * that less than or equal to the argument and is equal to a mathematical
     * integer.
     */
    public static float floor(final float a) {
        if (USE_HACK) {
            return (int) (a + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
        }
        if (useFastMath) {
            if (CHECK) {
                final float floor = floor_f(a);
                final float floor_math = (float) Math.floor(a);
                if (floor != floor_math) {
                    MarlinUtils.logInfo("floor(float) is wrong: " + floor + " :: "
                    + floor_math + " x: " + a);
                }
                return floor_math;
            }
            return floor_f(a);
        }
        return (float) Math.floor(a);
    }

    /**
     * Returns the smallest (closest to negative infinity) {@code float} value
     * that is greater than or equal to the argument and is equal to a
     * mathematical integer. Special cases:
     * <ul><li>If the argument value is already equal to a mathematical integer,
     * then the result is the same as the argument.  <li>If the argument is NaN
     * or an infinity or positive zero or negative zero, then the result is the
     * same as the argument.  <li>If the argument value is less than zero but
     * greater than -1.0, then the result is negative zero.</ul> Note that the
     * value of {@code StrictMath.ceil(x)} is exactly the value of
     * {@code -StrictMath.floor(-x)}.
     *
     * @param a a value.
     * @return the smallest (closest to negative infinity) floating-point value
     * that is greater than or equal to the argument and is equal to a
     * mathematical integer.
     */
    private static int ceil_f(final float a) {
        // Derived from StrictMath.ceil(double):

        // Inline call to Math.getExponent(a) to
        // compute only once Float.floatToRawIntBits(a)
        final int doppel = Float.floatToRawIntBits(a);

        final int exponent = ((doppel & FloatConsts.EXP_BIT_MASK)
                                >> (FloatConsts.SIGNIFICAND_WIDTH - 1))
                                - FloatConsts.EXP_BIAS;

        if (exponent < 0) {
            /*
             * Absolute value of argument is less than 1.
             * floorOrceil(-0.0) => -0.0
             * floorOrceil(+0.0) => +0.0
             */
            return ((a <= 0f) ? 0 : 1);
        }
        if (CHECK_OVERFLOW && (exponent >= 23)) { // 52 for double
            /*
             * Infinity, NaN, or a value so large it must be integral.
             */
            return ((a < 0f) ? Integer.MIN_VALUE : Integer.MAX_VALUE);
        }
        // Else the argument is either an integral value already XOR it
        // has to be rounded to one.
        assert exponent >= 0 && exponent <= 22; // 51 for double

        final int intpart = doppel
                            & (~(FloatConsts.SIGNIF_BIT_MASK >> exponent));

        if (intpart == doppel) {
            return (int) a; // integral value
        }

        // sign: 1 for negative, 0 for positive
        // add : 0 for negative and 1 for positive
        return (int) Float.intBitsToFloat(intpart) + ((~(intpart >> 31)) & 1);
    }

    /**
     * Returns the largest (closest to positive infinity) {@code float} value
     * that is less than or equal to the argument and is equal to a mathematical
     * integer. Special cases:
     * <ul><li>If the argument value is already equal to a mathematical integer,
     * then the result is the same as the argument.  <li>If the argument is NaN
     * or an infinity or positive zero or negative zero, then the result is the
     * same as the argument.</ul>
     *
     * @param a a value.
     * @return the largest (closest to positive infinity) floating-point value
     * that less than or equal to the argument and is equal to a mathematical
     * integer.
     */
    private static float floor_f(final float a) {
        // Derived from StrictMath.floor(double):

        // Inline call to Math.getExponent(a) to
        // compute only once Float.floatToRawIntBits(a)
        final int doppel = Float.floatToRawIntBits(a);

        final int exponent = ((doppel & FloatConsts.EXP_BIT_MASK)
                                >> (FloatConsts.SIGNIFICAND_WIDTH - 1))
                                - FloatConsts.EXP_BIAS;

        if (exponent < 0) {
            /*
             * Absolute value of argument is less than 1.
             * floorOrceil(-0.0) => -0.0
             * floorOrceil(+0.0) => +0.0
             */
            return ((a == 0f) ? 0f : ((a < 0f) ? -1f : 0f));
        }
        if (CHECK_OVERFLOW && (exponent >= 23)) { // 52 for double
            /*
             * Infinity, NaN, or a value so large it must be integral.
             */
            return a;
        }
        // Else the argument is either an integral value already XOR it
        // has to be rounded to one.
        assert exponent >= 0 && exponent <= 22; // 51 for double

        final int intpart = doppel
                            & (~(FloatConsts.SIGNIF_BIT_MASK >> exponent));

        if (intpart == doppel) {
            return a; // integral value
        }

        // sign: 1 for negative, 0 for positive
        // add : -1 for negative and 0 for positive
        return Float.intBitsToFloat(intpart) + (intpart >> 31);
    }

}
