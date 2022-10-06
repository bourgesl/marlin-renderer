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
import org.marlin.pisces.Solver.Function;

/**
 * Generic Polynom class
 */
final class Polynom implements Function {

    static final boolean HIGH_PRECISION = true;

    static final boolean USE_QUAD_ANALYTICAL = false;
    static final boolean DO_STATS = false;

    // Math.ulp(0.5) is ultimate precision on t in [0,1]
    private static final double EPS = (HIGH_PRECISION) ? Math.ulp(0.5) : 1e-6; // or 1e-2

    private static final boolean RETURN_AB_IF_NON_ZERO = !Polynom.HIGH_PRECISION;

    private static long EVAL_COUNT = 0;

    static {
        // log settings:
        System.out.println("--- Polynom/Solver settings ---");

        System.out.println("HIGH_PRECISION        = " + HIGH_PRECISION);
        System.out.println("USE_QUAD_ANALYTICAL   = " + USE_QUAD_ANALYTICAL);
        System.out.println("RETURN_AB_IF_NON_ZERO = " + RETURN_AB_IF_NON_ZERO);
        System.out.println("EPS                   = " + EPS);
        System.out.println("DO_STATS              = " + DO_STATS);

        System.out.println("DEBUG_SOLVER = " + Solver.DEBUG_SOLVER);
        System.out.println("INFO_SOLVER  = " + Solver.INFO_SOLVER);

        System.out.println("--- ---");
    }

    // members
    final int degree;
    final double ai[];
    private final Function function;
    private boolean set;
    // cached values:
    private double conditionNumber = Double.NaN;
    private Polynom derivative = null;

    Polynom(double... ai) {
        this(ai.length - 1);
        set(ai);
    }

    Polynom(final int degree) {
        if (degree <= 0 || degree > 3) {
            throw new UnsupportedOperationException("Polynom of degree " + degree + " not supported !");
        }
        this.degree = degree;
        this.ai = new double[degree + 1];
        this.set = false;

        final double[] a = ai; // stable final reference
        switch (degree) {
            case 1:
                this.function = (double t) -> {
                    if (DO_STATS) {
                        EVAL_COUNT++;
                    }
                    return DHelpers.evalLine(a[0], a[1], t);
                };
                break;
            case 2:
                this.function = (double t) -> {
                    if (DO_STATS) {
                        EVAL_COUNT++;
                    }
                    return DHelpers.evalQuad(a[0], a[1], a[2], t);
                };
                break;
            case 3:
                this.function = (double t) -> {
                    if (DO_STATS) {
                        EVAL_COUNT++;
                    }
                    return DHelpers.evalCubic(a[0], a[1], a[2], a[3], t);
                };
                break;
            default:
                this.function = null;
        }
    }

    public int getDegree() {
        return degree;
    }

    public boolean isSet() {
        return set;
    }

    private void setSet(final boolean set) {
        this.set = set;
        // reset cached values:
        this.conditionNumber = Double.NaN;
        if (derivative != null) {
            derivative.setSet(false);
        }
    }

    void set(double a0, double a1) {
        if (degree == 1) {
            final double[] a = ai; // stable final reference
            a[0] = a0;
            a[1] = a1;
            setSet(true);
        } else {
            throw new UnsupportedOperationException("Polynom of degree " + degree + " can not have 2 coefficients !");
        }
    }

    void set(double a0, double a1, double a2) {
        if (degree == 2) {
            final double[] a = ai; // stable final reference
            a[0] = a0;
            a[1] = a1;
            a[2] = a2;
            setSet(true);
        } else {
            throw new UnsupportedOperationException("Polynom of degree " + degree + " can not have 3 coefficients !");
        }
    }

    void set(double a0, double a1, double a2, double a3) {
        if (degree == 3) {
            final double[] a = ai; // stable final reference
            a[0] = a0;
            a[1] = a1;
            a[2] = a2;
            a[3] = a3;
            setSet(true);
        } else {
            throw new UnsupportedOperationException("Polynom of degree " + degree + " can not have 4 coefficients !");
        }
    }

    void set(double... ai) {
        if (degree == ai.length - 1) {
            for (int i = 0; i < ai.length; i++) {
                this.ai[i] = ai[i];
            }
            setSet(true);
        } else {
            throw new UnsupportedOperationException("Polynom of degree " + degree + " can not have " + ai.length + " coefficients !");
        }
    }

    Polynom mul(final Polynom p) {
        final int maxDegree = degree + p.degree;
        final double[] bi = new double[maxDegree + 1]; // 0.0

        final double[] a = ai; // stable final reference
        final double[] b = p.ai; // stable final reference

        for (int i = 0; i < a.length; ++i) {
            for (int j = 0; j < b.length; ++j) {
                bi[i + j] += a[i] * b[j];
            }
        }
        return new Polynom(bi);
    }

    double getConditionNumber() {
        if (Double.isNaN(conditionNumber)) {
            double sum = 0.0;
            final double[] a = ai; // stable final reference

            for (int i = 0; i < a.length; ++i) {
                sum += Math.abs(a[i]); // [0,1] interval
            }
            conditionNumber = sum;
        }
        return conditionNumber;
    }

    double ulp() {
        return Math.ulp(getConditionNumber());
    }

    @Override
    public double eval(final double t) {
        return this.function.eval(t);
    }

    public Polynom getDerivative() {
        if (derivative == null) {
            switch (degree) {
                default:
                    return null;
                case 2:
                    derivative = new Polynom(1);
                    break;
                case 3:
                    derivative = new Polynom(2);
                    break;
            }
        }
        if (!derivative.isSet()) {
            final double[] a = ai; // stable final reference

            switch (degree) {
                default:
                case 2:
                    derivative.set(2.0 * a[0], a[1]);
                    break;
                case 3:
                    derivative.set(3.0 * a[0], 2.0 * a[1], a[2]);
                    break;
            }
            // System.out.println("getDerivative(): " + derivative);
        }
        return derivative;
    }

    public int roots(final double[] roots, final byte[] types, final int off) {
        return roots(roots, types, off, EPS, RETURN_AB_IF_NON_ZERO);
    }

    public int roots(final double[] roots, final byte[] types, final int off, final boolean returnABIfNotZero) {
        return roots(roots, types, off, EPS, returnABIfNotZero);
    }
    
    public int roots(final double[] roots, final byte[] types, final int off, final double err, final boolean returnABIfNotZero) {
        switch (degree) {
            case 2:
                if (USE_QUAD_ANALYTICAL) {
                    final double[] a = ai;
                    return DHelpers.quadraticRoots(a[0], a[1], a[2], roots, off);
                } else {
                    // slower but more accurate as it can give interval [t-,t+]
                    final int doff = off + 1; // as 2 roots can be written at once
                    final int num = getDerivative().roots(roots, null, doff, err, false);
                    return Solver.roots(roots, types, off, doff, doff + num, function, err, returnABIfNotZero);
                }
            case 3:
                final int doff = off + 1; // as 2 roots can be written at once
                final int num = getDerivative().roots(roots, null, doff, err, false);
                return Solver.roots(roots, types, off, doff, doff + num, function, err, returnABIfNotZero);
            case 1: {
                final double[] a = ai;
                return DHelpers.linearRoots(a[0], a[1], roots, off);
            }
            default:
                return 0;
        }
    }

    public int rootsAnalytical(final double[] roots, final int off) {
        final double[] a = ai;
        switch (degree) {
            case 2:
                return DHelpers.quadraticRoots(a[0], a[1], a[2], roots, off);
            case 3:
                return DHelpers.cubicRootsInAB(a[0], a[1], a[2], a[3], roots, off);
            case 1:
                return DHelpers.linearRoots(a[0], a[1], roots, off);
            default:
                return 0;
        }
    }

    @Override
    public String toString() {
        return "Polynom{"
                + "degree=" + degree
                + ", set=" + set
                + ", ai=" + Arrays.toString(ai)
                + ", cond=" + getConditionNumber()
                + ", ulp=" + ulp()
                // + ", derivative=" + derivative
                + '}';
    }

    public static void main(String[] args) {
        System.out.println("EPS(1)  = " + Math.ulp(1.0));
        System.out.println("EPS(0.5)= " + Math.ulp(0.5));
        System.out.println("EPS(1-) = " + EPS);

        System.out.println("Double.MAX_VALUE = " + Double.MAX_VALUE);
        System.out.println("Double.MIN_VALUE = " + Double.MIN_VALUE);

        final Polynom cubic = new Polynom(1.0, -0.117).mul(new Polynom(1.0, -0.69)).mul(new Polynom(1.0, -0.39));
        System.out.println("cubic : " + cubic);

        final double[] roots = new double[6]; // 3 x 2 (AB)
        final byte[] types = new byte[6]; // 3 x 2 (AB)

        Polynom polynom = cubic;
        while (polynom != null) {
            System.out.println("polynom : " + polynom);

            int num = polynom.roots(roots, types, 0);
            num = DHelpers.isort(roots, num); // sort and remove duplicates
            final double[] sRoots = Arrays.copyOfRange(roots, 0, num);
            System.out.println("solver   roots: (" + num + ") = " + Arrays.toString(sRoots));

            if (DO_STATS) {
                System.out.println("solver eval count: " + EVAL_COUNT);
                // Reset
                EVAL_COUNT = 0;
            }

            int numA = polynom.rootsAnalytical(roots, 0);
            numA = DHelpers.isort(roots, numA); // sort and remove duplicates
            final double[] aRoots = Arrays.copyOfRange(roots, 0, numA);
            System.out.println("analytic roots: (" + numA + ") = " + Arrays.toString(aRoots));

            for (int i = 0, end = Math.min(num, numA); i < end; i++) {
                System.out.println("p(" + sRoots[i] + "): " + polynom.eval(sRoots[i]));
                System.out.println("p(" + aRoots[i] + "): " + polynom.eval(aRoots[i]));

                final double delta = Math.abs(sRoots[i] - aRoots[i]);
                System.out.println("delta: " + delta + " ulp: " + delta / Math.ulp(Math.min(sRoots[i], aRoots[i])));
            }

            polynom = polynom.getDerivative();
        }

        if (!Solver.DEBUG_SOLVER) {
            if (DO_STATS) {
                // Reset
                EVAL_COUNT = 0;
            }

            final int R = 10;
            final int N = 1000000;

            long sum1 = 0l;
            long sum2 = 0l;

            for (int r = 0; r < R; r++) {
                {
                    long start = System.nanoTime();
                    int num = 0;
                    double res = 0.0;

                    for (int i = 0; i < N; i++) {
                        num = cubic.roots(roots, types, 0);
                        if (num != 0) {
                            res += roots[0];
                        }
                    }

                    final long elapsed = (System.nanoTime() - start);
                    sum1 += elapsed;
                    System.out.println("rootsSolver()     duration = " + elapsed + " ns.");
                    System.out.println("res = " + res);
                    System.out.println("roots = " + Arrays.toString(Arrays.copyOfRange(roots, 0, num)));
                }
                {
                    long start = System.nanoTime();
                    int num = 0;
                    double res = 0.0;

                    for (int i = 0; i < N; i++) {
                        num = cubic.rootsAnalytical(roots, 0);
                        if (num != 0) {
                            res += roots[0];
                        }
                    }

                    final long elapsed = (System.nanoTime() - start);
                    sum2 += elapsed;
                    System.out.println("rootsAnalytical() duration = " + elapsed + " ns.");
                    System.out.println("res = " + res);
                    System.out.println("roots = " + Arrays.toString(Arrays.copyOfRange(roots, 0, num)));
                }
            }

            System.out.println("rootsSolver()     duration = " + (sum1 / (R * N)) + " ns/op");
            System.out.println("rootsAnalytical() duration = " + (sum2 / (R * N)) + " ns/op");

            if (DO_STATS) {
                System.out.println("solver eval count: " + (EVAL_COUNT * 1.0 / (R * N)) + " /op");
            }
        }
    }
}
