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
package test.math;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * @summary This is not a test. This is an exploratory task to empirically
 *          identify what is the accuracy of the findExtrema() function.
 *          (cubic and quad curves)
 *
 * @author @mickleness @bourgesl
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StrokedCurveTest extends BaseTest {

    private final static float WIDTH = 2.0f;

    private final static double HALF_WIDTH = ((double) WIDTH) / 2.0;
    private final static double TH_RATIO = 5.0;

    private final static double EPS = Math.ulp(1.0);

    private final static boolean DEBUG = false;

    private final static boolean QUIET_RUN = true & !DEBUG;

    private final static int N = 1000 * 1000 * 10;
    private final static int M = N / 10;

    private static double TH = TH_RATIO;
    private static int N_LOG = 0;
    private final static int MAX_LOG = 100;

    /** statistics on ratio(delta / half width) */
    private static final WelfordVariance ratioCondStats = new WelfordVariance();

    /**
     * This iterates through million random curves to determine the
     * condition-number needed to estimate an upper-limit of the 
     * numerical accuracy.
     * @param args unused
     */
    public static void main(String[] args) {
        // First display which renderer is tested:
        // JDK9 only:
        System.setProperty("sun.java2d.renderer.verbose", "true");
        System.out.println("Testing renderer: ");
        // Other JDK:
        String renderer = "undefined";
        try {
            renderer = sun.java2d.pipe.RenderingEngine.getInstance().getClass().getName();
            System.out.println(renderer);
        } catch (Throwable th) {
            // may fail with JDK9 jigsaw (jake)
            if (false) {
                System.err.println("Unable to get RenderingEngine.getInstance()");
                th.printStackTrace();
            }
        }
        
        System.out.println("-------------------");
        System.out.println(StrokedCurveTest.class.getSimpleName() + ": start");
        System.out.println("N:         " + N);

        System.out.println("QUIET_RUN: " + QUIET_RUN);
        System.out.println("DEBUG:     " + DEBUG);

        System.out.println("HALF_WIDTH:" + HALF_WIDTH);
        System.out.println("TH_RATIO:  " + TH_RATIO);
        System.out.println("EPS:       " + EPS);

        final StrokedCurveContext ctx = new StrokedCurveContext();

        // original:
        if (false) {
            test(ctx, 1.0, 1);
            test(ctx, 1.0, 2.0 * EPS);
        }

        // 15 initially:
        for (int i = 15; i >= 2; i--) {
            final double scale = Math.pow(10.0, -i);
            test(ctx, 1.0, scale);
        }

        for (int i = 2; i <= 15; i++) {
            final double scale = Math.pow(10.0, i);
            test(ctx, scale, 1.0);
        }

        test(ctx, 1e6, 10);
        test(ctx, 1e6, 1e-3);
        test(ctx, 1e6, 1e6);
        test(ctx, 1e6, 1e9);

        test(ctx, 1e9, 10);
        test(ctx, 1e9, 1e6);
        test(ctx, 1e9, 1e9);

        test(ctx, 1e15, 10);
        test(ctx, 1e15, 1e3);

        // test scaling:
        for (double scale = 1e6; scale <= 1e16; scale *= 10.0) {
            test(ctx, scale, scale);
        }
        System.out.println(StrokedCurveTest.class.getSimpleName() + ": done");
        System.out.println("-------------------");
    }

    private static void test(final StrokedCurveContext ctx,
                             final double off, final double rng) {
        System.out.println("-------------------");
        System.out.println("offset value: " + off);
        System.out.println("random scale: " + rng);

        resetStats();

        // Initialize random context once (seed fixed):
        final RandomContext rc = new RandomContext(8);
        // reset threshold to initial value:
        TH = TH_RATIO;
        N_LOG = 0;

        for (int n = 0; n < N; n++) {
            test(ctx, n, rc, off, rng);

            if (n % M == 0) {
                System.out.println("Iteration " + n + " ---");
                // dumpStats(false);
            }
        }
        System.out.println("Test done ---");
        dumpStats(true);
    }

    private static void dumpStats(boolean verbose) {
        System.out.println("ratioCond:     " + ratioCondStats.max());

        if (verbose) {
            System.out.println("stats(ratio):  " + ratioCondStats);
        }
    }

    private static void resetStats() {
        ratioCondStats.reset();
    }

    private static void test(final StrokedCurveContext ctx, int trial,
                             final RandomContext rc, final double off, final double rng) {

        final double half = rng / 2.0;

        final double x0 = off;
        final double x1 = off + (rc.nextDouble(1) * rng - half);
        final double x2 = off + (rc.nextDouble(2) * rng - half);
        final double x3 = off + (rc.nextDouble(3) * rng - half);

        final double y0 = off;
        final double y1 = off + (rc.nextDouble(5) * rng - half);
        final double y2 = off + (rc.nextDouble(6) * rng - half);
        final double y3 = off + (rc.nextDouble(7) * rng - half);

        test(trial, ctx, x0, y0, x1, y1, x2, y2, x3, y3);
    }

    private static void test(final int trial,
                             final StrokedCurveContext ctx,
                             final double x1, final double y1,
                             final double x2, final double y2,
                             final double x3, final double y3,
                             final double x4, final double y4) {

        final Path2D.Double path = ctx.path;
        final Stroke stroke = ctx.stroke;

        // initialize path:
        path.reset();
        path.moveTo(x1, x1);
        path.curveTo(x2, y2, x3, y3, x4, y4);

        final Rectangle2D pathBBox = path.getBounds2D();

        // get stroked path:
        final Shape strokedPath = stroke.createStrokedShape(path);

        final Rectangle2D strokedPathBBox = strokedPath.getBounds2D();

        // Compare bbox within margin = half width ?
        final double delta = maxDistance(pathBBox, strokedPathBBox) - HALF_WIDTH;

        // System.out.println("delta:\t" + delta);
        // update stats:
        final double uratio = delta / HALF_WIDTH;

        if (delta > 0.0) {
            ratioCondStats.add(uratio);
        }

        if (QUIET_RUN && (uratio < TH) || (!DEBUG && (delta < 0.0)) || (N_LOG >= MAX_LOG)) {
            // test OK
            return;
        }

        System.out.println("Examining (trial #" + trial + "), delta: " + delta);
        System.out.println("path:    " + toString(x1, y1, x2, y2, x3, y3, x4, y4));
        System.out.println("path2:   " + toString(path));
        System.out.println("stroked: " + toString(strokedPath));

        System.out.println("Cond[" + ((delta <= 0.0) ? "OK" : " KO") + "]:\t" + uratio + " delta:\t" + delta);

        // increase threshold:
        TH = uratio;
        N_LOG++;
    }

    private static double maxDistance(Rectangle2D src, Rectangle2D dst) {
        double max = 0.0;
        max = Math.max(max, Math.abs(src.getMinX() - dst.getMinX()));
        max = Math.max(max, Math.abs(src.getMaxX() - dst.getMaxX()));
        max = Math.max(max, Math.abs(src.getMinY() - dst.getMinY()));
        max = Math.max(max, Math.abs(src.getMaxY() - dst.getMaxY()));
        return max;
    }

    private StrokedCurveTest() {
        super();
    }

    // Test context
    private static final class StrokedCurveContext {

        final Path2D.Double path = new Path2D.Double();

        // no cap nor joins to reduce bbox variations:
        final Stroke stroke = new BasicStroke(WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    }
}
