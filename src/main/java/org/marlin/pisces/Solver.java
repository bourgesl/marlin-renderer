/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Fast and approximate polynomial solver ensuring returning roots as intervals [t-, t+]
 */
final class Solver {

    static final boolean DEBUG_SOLVER = false;
    static final boolean INFO_SOLVER = false;

    private Solver() {
        super();
    }

    @FunctionalInterface
    public interface Function {

        double eval(final double t);
    }

    // Tries to find the roots of the polynomial function ]0, 1[. It uses
    // a variant of the false position algorithm to find the roots. False
    // position requires that initial values x be given, and that the
    // function must have opposite signs at those values. To find such
    // values, we need the local extrema of the polynomial function, 
    // for which we need the roots of its derivative, given in roots[off, end]. 
    // So we will check at most n sub-intervals of [0,1].
    static int roots(final double[] roots, final byte[] types, final int off, int doff, int end,
                     final Function function, final double err, final boolean returnABIfNotZero) {
        // no OOB exception, because by now off<=6, and roots.length >= 10
        // assert off <= 6 && roots.length >= 10;

        int ret = off;
        // sort roots within [0,1]:
        end = DHelpers.isort(roots, doff, end);

        roots[end] = 1.0; // always check interval end points

        if (DEBUG_SOLVER) {
            System.out.println("roots: function: " + function);
            System.out.println("roots: interval t: " + Arrays.toString(Arrays.copyOfRange(roots, doff, end)));
        }

        double t0 = 0.0, ft0 = function.eval(t0);

        for (int i = doff; i <= end; i++) {
            double t1 = roots[i], ft1 = function.eval(t1);

            if (DEBUG_SOLVER) {
                System.out.println("t0: " + t0 + "\t f(t0): " + ft0);
                System.out.println("t1: " + t1 + "\t f(t1): " + ft1);
            }
            if (ft0 == 0.0) {
                roots[ret++] = t0;
            } else if (ft1 * ft0 < 0.0) { // have opposite signs
                ret += falsePosition(roots, types, ret,
                        t0, eliminateInf(ft0),
                        t1, eliminateInf(ft1),
                        function, err, returnABIfNotZero);
            }
            t0 = t1;
            ft0 = ft1;
        }

        if (false && DEBUG_SOLVER) {
            final double step = 1.0 / 1000;
            final double dstep = step / 100;

            for (double t = 0.0; t <= 1.0; t += step) {
                final double ft = function.eval(t);
                final double dft = (function.eval(t + dstep) - ft) / dstep;
                System.out.println("t: " + t + "\t f(t) = " + ft + "\t df(t) = " + dft);
            }
        }

        return ret - off;
    }

    // A slight modification of the false position algorithm on wikipedia.
    // This only works for the ROCsq-x functions. It might be nice to have
    // the function as an argument, but that would be awkward in java6.
    // TODO: It is something to consider for java8 (or whenever lambda
    // expressions make it into the language), depending on how closures
    // and turn out. Same goes for the newton's method
    // algorithm in DHelpers.java
    @SuppressWarnings("AssignmentToMethodParameter")
    private static int falsePosition(final double[] roots, final byte[] types, final int off,
                                     double a, double fa,
                                     double b, double fb,
                                     final Function function,
                                     final double err, final boolean returnABIfNotZero) {
        final int iterLimit = 100;
        int side = 0;
 /*
            abs_error=|b-a|, x = (a+b)/2
            rel_error=2*|b-a|/(a+b)
            => rel_error < err ie |b-a| <= err * (a+b)
         */
 /* a & b in [0,1] so (b+a) > 0 */

        // LBO: use absolute error (faster):
        for (int i = 0; (i < iterLimit) && (Math.abs(b - a) > err /* * (b + a) */); i++) {
            final double x = (fa * b - fb * a) / (fa - fb);
            final double fx = eliminateInf(function.eval(x));

            if (DEBUG_SOLVER) {
                System.out.println("falsePosition[" + i + "]: x = " + x + " f(x) = " + fx);
            }

            if (sameSign(fx, fb)) {
                fb = fx;
                b = x;
                if (side < 0) {
                    fa /= (1 << (-side));
                    side--;
                } else {
                    side = -1;
                }
            } else if (fx * fa > 0.0) {
                fa = fx;
                a = x;
                if (side > 0) {
                    fb /= (1 << side);
                    side++;
                } else {
                    side = 1;
                }
            } else {
                // 0.0
                if (DEBUG_SOLVER) {
                    System.out.println("falsePosition: x = " + x);
                }
                roots[off] = x;
                return 1;
            }
            if (false && DEBUG_SOLVER) {
                System.out.println("falsePosition[" + i + "]: a = " + a + " f(a) = " + fa);
                System.out.println("falsePosition[" + i + "]: b = " + b + " f(b) = " + fb);
            }

        }
        if (INFO_SOLVER || DEBUG_SOLVER) {
            System.out.println("falsePosition: - a = " + a + " (" + Double.doubleToRawLongBits(a) + ")\t f(a) = " + fa);
            System.out.println("falsePosition: - b = " + b + " (" + Double.doubleToRawLongBits(b) + ")\t f(b) = " + fb);

            double delta = (b - a);
            System.out.println("delta: " + delta + " ulp: " + delta / Math.ulp(a));
        }

        if (returnABIfNotZero) {
            roots[off] = a;
            types[off] = 1;
            roots[off + 1] = b;
            types[off + 1] = -1;
            return 2;
        } else {
            if (Math.abs(fb) < Math.abs(fa)) {
                if (DEBUG_SOLVER) {
                    System.out.println("falsePosition: b = " + b);
                }
                roots[off] = b;
            } else {
                if (DEBUG_SOLVER) {
                    System.out.println("falsePosition: a = " + a);
                }
                roots[off] = a;
            }
            return 1;
        }
    }

    private static boolean sameSign(final double x, final double y) {
        // another way is to test if x*y > 0. This is bad for small x, y.
        return (x < 0.0 && y < 0.0) || (x > 0.0 && y > 0.0);
    }

    private static double eliminateInf(final double x) {
        return (x == Double.POSITIVE_INFINITY ? Double.MAX_VALUE
                : (x == Double.NEGATIVE_INFINITY ? Double.MIN_VALUE : x));
    }

}
