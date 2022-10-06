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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import static java.awt.geom.PathIterator.SEG_CLOSE;
import static java.awt.geom.PathIterator.SEG_CUBICTO;
import static java.awt.geom.PathIterator.SEG_LINETO;
import static java.awt.geom.PathIterator.SEG_MOVETO;
import static java.awt.geom.PathIterator.SEG_QUADTO;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * @summary This is not a test. This is an exploratory task to empirically
 *          identify what is the accuracy of the findExtrema() function.
 *          (cubic and quad curves)
 *
 * @author @mickleness @bourgesl
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StrokedCurveTest {

    private final static boolean TEST = true;
    private final static boolean DEBUG = false || TEST;

    private final static float WIDTH = 2.0f;

    private final static double HALF_WIDTH = ((double) WIDTH) / 2.0;
    private final static double TH_RATIO = 5.0;

    private final static double EPS = Math.ulp(1.0);

    private final static boolean QUIET_RUN = true & !DEBUG;

    private final static int N = 1000 * 1000 * 10;
    private final static int M = N / 10;

    private static double TH = TH_RATIO;
    private static int N_LOG = 0;
    private final static int MAX_LOG = 100;

    private static int N_TEST = 0;

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

        if (TEST) {
            // test(0, ctx, 1.0, 1.0, 1.000000000000033, 0.9999999999999702, 1.0000000000000189, 0.9999999999999561, 1.0000000000000018, 1.00000000000004 );

            test(0, ctx, 1.0, 1.0, 1.0000000000000044, 0.9999999999999979, 0.9999999999999966, 1.0000000000000002, 1.0000000000000049, 0.9999999999999979);

            // test(0, ctx, 1.000000000000001, 0.999999999999999, 1.0000000000000007, 0.9999999999999991, 1.0000000000000009, 0.999999999999999, 1.000000000000002, 0.9999999999999987);
            // 
            System.exit(0);
        }
        
  /*      
--------------------------------------------------
p2d.moveTo(380.0, 1050.0);
p2d.curveTo(1793.0, 1359.0, 345.0, 1957.0, 1828.0, 724.0);
--------------------------------------------------
*/        
/* 2 ears (w = max)
--------------------------------------------------
p2d.moveTo(250.0, 750.0);
p2d.curveTo(1432.0, 1067.0, 439.0, 1404.0, 1313.0, 627.0);
--------------------------------------------------  
  p2d.moveTo(250.0, 750.0);
p2d.curveTo(3772.0, 1504.0, 787.0, 1925.0, 3328.0, 613.0);

  */        
        

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
        N_TEST++;

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
        path.moveTo(x1, y1);
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
        System.out.println("path:    " + BaseTest.toString(x1, y1, x2, y2, x3, y3, x4, y4));
        System.out.println("path2:   " + BaseTest.toString(path));
        System.out.println("stroked: " + BaseTest.toString(strokedPath));

        System.out.println("Cond[" + ((delta <= 0.0) ? "OK" : " KO") + "]:\t" + uratio + " delta:\t" + delta);
        System.out.println("--------------------------------------------------");

        // paint test case on image:
        ctx.painter.paint("path", trial, delta, HALF_WIDTH, path, null);
        ctx.painter.paint("all", trial, delta, HALF_WIDTH, path, strokedPath);

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

    // Test context
    private static final class StrokedCurveContext {

        final Path2D.Double path = new Path2D.Double();

        // no cap nor joins to reduce bbox variations:
        final Stroke stroke = new BasicStroke(WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

        final StrokedCurvePainter painter = new StrokedCurvePainter();
    }

    private static final class StrokedCurvePainter {

        static final int W = 1000;
        static final int M = 10;

        static final boolean SHOW_STROKED = true;
        static final boolean SHOW_OUTLINE = true;
        static final boolean SHOW_POINTS = true;

        static final Color COLOR_MOVETO = new Color(255, 0, 0, 160);
        static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, 160);
        static final Color COLOR_LINETO_EVEN = new Color(0, 255, 0, 160);

        static final Color COLOR_STROKED = new Color(192, 0, 192, 192);

        // members:
        private final BufferedImage image;
        private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
        private final Line2D.Double line = new Line2D.Double();
        private final Rectangle2D.Double rect = new Rectangle2D.Double();

        StrokedCurvePainter() {
            final float lineStroke = 2f;
            final float miterLimit = 5f;

            final int width = W;
            final int height = W;

            final int cap = BasicStroke.CAP_SQUARE; // No cap
            final int join = BasicStroke.JOIN_BEVEL; // bevel

            System.out.println("StrokedCurvePainter: width = " + width);
            System.out.println("StrokedCurvePainter: height = " + height);

            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        public void paint(final String label, final int n, final double score,
                          final double halfWidth,
                          final Shape inputPath, final Shape strokedPath) {

            final Graphics2D g2d = (Graphics2D) image.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

            g2d.setClip(0, 0, W, W);
            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, W, W);

            paint(g2d, inputPath, strokedPath);

            try {
                final double score_rnd = ((int) (score * 1000.0)) / 1000.0;
                final File file = new File("StrokedCurveTest-" + label + "=" + score_rnd + "-" + N_TEST + "_" + n + ".png");

                System.out.println("Writing file: " + file.getAbsolutePath());
                ImageIO.write(image, "PNG", file);
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                g2d.dispose();
            }
        }

        private void paint(final Graphics2D g2d, final Shape inputPath, final Shape strokedPath) {

            final Rectangle2D bbox = (strokedPath != null) ? strokedPath.getBounds2D() : inputPath.getBounds2D();
            final double minX = bbox.getMinX();
            final double minY = bbox.getMinY();
            final double size = Math.max(bbox.getWidth(), bbox.getHeight());

            System.out.println("min: (" + minX + ", " + minY + ")");

            if (size <= 0.0) {
                return;
            }

            final AffineTransform prevAt = g2d.getTransform();

            final double scale = (W - (M * 2)) / size;
            System.out.println("scale: " + scale);

            // translate:
            g2d.translate(+M, +M);

            g2d.scale(scale, scale);
            g2d.translate(-minX, -minY);

            // add margin:
            final BasicStroke stroke = new BasicStroke((float) (2.0 / scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, null, 0);
            g2d.setStroke(stroke);

            if (SHOW_STROKED && (strokedPath != null)) {
                // Paint expected rect:
                final Rectangle2D path_bbox = inputPath.getBounds2D();
                g2d.setColor(Color.RED);
                rect.setFrame(path_bbox.getMinX() - HALF_WIDTH, path_bbox.getMinY() - HALF_WIDTH,
                        path_bbox.getWidth() + 2.0 * HALF_WIDTH, path_bbox.getHeight() + 2.0 * HALF_WIDTH);
                g2d.draw(rect);

                if (true) {
                    // Paint stroked path:
                    g2d.setColor(Color.GRAY);
                    g2d.fill(strokedPath);
                } else {
                    // Paint scaled stroked path (over):
                    g2d.setColor(COLOR_STROKED);

                    // TODO: fix scaling of the shape and stroke ?
                    final Shape strokedScaledPath = stroke.createStrokedShape(inputPath);

                    System.out.println("stroked: " + BaseTest.toString(strokedScaledPath));
                    g2d.fill(strokedScaledPath);
                }
            }

            if (SHOW_OUTLINE && (strokedPath != null)) {
                g2d.setColor(COLOR_LINETO_ODD);
                g2d.draw(strokedPath);
            }

            final double pw = 10.0 / scale;

            dumpPoints(g2d, inputPath, "INPUT", pw);

            if (SHOW_OUTLINE && (strokedPath != null)) {
                dumpPoints(g2d, strokedPath, "STROKED", pw);
            }

            // Paint input path (over):
            g2d.setColor(Color.RED);
            g2d.draw(inputPath);

            g2d.setTransform(prevAt);
        }

        @SuppressWarnings("fallthrough")
        private void dumpPoints(final Graphics2D g2d, final Shape path, final String prefix, final double pw) {
            int np = 0;
            final int[] nOps = new int[SEG_CLOSE + 1];

            final double[] coords = new double[6];
            double px = Double.NaN, py = Double.NaN;

            for (final PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
                final int type = it.currentSegment(coords);
                nOps[type]++;
                switch (type) {
                    case SEG_MOVETO:
                        if (SHOW_POINTS) {
                            g2d.setColor(COLOR_MOVETO);
                        }
                        break;
                    case SEG_LINETO:
                    case SEG_QUADTO:
                    case SEG_CUBICTO:
                        if (SHOW_POINTS) {
                            g2d.setColor((nOps[type] % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                        }
                        if (type == SEG_LINETO) {
                            break;
                        }
                        line.setLine(px, py, coords[0], coords[1]); // P1 - P2
                        g2d.draw(line);
                        line.setLine(coords[0], coords[1], coords[2], coords[3]); // P2 - P3
                        g2d.draw(line);
                        if (type == SEG_CUBICTO) {
                            line.setLine(coords[2], coords[3], coords[4], coords[5]); // P3 - P4
                            g2d.draw(line);
                        }
                        break;
                    default:
                        System.out.println("unsupported segment type= " + type);
                    case PathIterator.SEG_CLOSE:
                        continue;
                }
                // get point:
                px = coords[0];
                py = coords[1];
                System.out.println("point[" + (np++) + " seg=" + type + "]: (" + px + " " + py + ")");

                if (SHOW_POINTS) {
                    ellipse.setFrame(px - pw / 2.0, py - pw / 2.0, pw, pw);
                    g2d.fill(ellipse);
                }
            }
            System.out.println(prefix
                    + " Path moveTo=" + nOps[SEG_MOVETO]
                    + ", lineTo=" + nOps[SEG_LINETO]
                    + ", quadTo=" + nOps[SEG_QUADTO]
                    + ", curveTo=" + nOps[SEG_CUBICTO]
                    + ", close=" + nOps[SEG_CLOSE]
            );
            System.out.println("--------------------------------------------------");
        }
    }

}
