/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * @test
 * @bug 8191814
 * @summary Verifies that Marlin rendering generates the same
 * images with and without clipping optimization with all possible
 * stroke (cap/join) and/or dashes or fill modes (EO rules)
 * for paths made of either 9 lines, 4 quads, 2 cubics (random)
 * Note: Use the argument -slow to run more intensive tests (too much time)
 *
 * @run main/othervm/timeout=120 -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine ClipShapeTest -poly
 * @run main/othervm/timeout=240 -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine ClipShapeTest -poly -doDash
 * @run main/othervm/timeout=120 -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine ClipShapeTest -cubic
 * @run main/othervm/timeout=240 -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine ClipShapeTest -cubic -doDash
 * @run main/othervm/timeout=120 -Dsun.java2d.renderer=sun.java2d.marlin.DMarlinRenderingEngine ClipShapeTest -poly
 * @run main/othervm/timeout=240 -Dsun.java2d.renderer=sun.java2d.marlin.DMarlinRenderingEngine ClipShapeTest -poly -doDash
 * @run main/othervm/timeout=120 -Dsun.java2d.renderer=sun.java2d.marlin.DMarlinRenderingEngine ClipShapeTest -cubic
 * @run main/othervm/timeout=240 -Dsun.java2d.renderer=sun.java2d.marlin.DMarlinRenderingEngine ClipShapeTest -cubic -doDash
 */
public final class ClipShapeTest {

    // test options:
    static int NUM_TESTS;

    // shape settings:
    static ShapeMode SHAPE_MODE;

    static boolean USE_DASHES;
    static boolean USE_VAR_STROKE;

    static int THRESHOLD_DELTA;
    static long THRESHOLD_NBPIX;

    // constants:
    static final boolean DO_FAIL = true;

    static final boolean TEST_STROKER = true;
    static final boolean TEST_FILLER = true;

    static final int TESTW = 100;
    static final int TESTH = 100;

    // dump path on console:
    static final boolean DUMP_SHAPE = true;

    static final boolean SHOW_DETAILS = false; // disabled
    static final boolean SHOW_OUTLINE = true;
    static final boolean SHOW_POINTS = true;
    static final boolean SHOW_INFO = false;

    static final int MAX_SHOW_FRAMES = 10;
    static final int MAX_SAVE_FRAMES = 100;

    // use fixed seed to reproduce always same polygons between tests
    static final boolean FIXED_SEED = true;

    static final double RAND_SCALE = 3.0;
    static final double RANDW = TESTW * RAND_SCALE;
    static final double OFFW = (TESTW - RANDW) / 2.0;
    static final double RANDH = TESTH * RAND_SCALE;
    static final double OFFH = (TESTH - RANDH) / 2.0;

    static enum ShapeMode {
        TWO_CUBICS,
        FOUR_QUADS,
        FIVE_LINE_POLYS,
        NINE_LINE_POLYS,
        FIFTY_LINE_POLYS,
        MIXED
    }

    static final long SEED = 1666133789L;
    // Fixed seed to avoid any difference between runs:
    static final Random RANDOM = new Random(SEED);

    static final File OUTPUT_DIR = new File(".");

    static final AtomicBoolean isMarlin = new AtomicBoolean();
    static final AtomicBoolean isClipRuntime = new AtomicBoolean();

    static {
        Locale.setDefault(Locale.US);

        // FIRST: Get Marlin runtime state from its log:

        // initialize j.u.l Looger:
        final Logger log = Logger.getLogger("sun.java2d.marlin");
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                final String msg = record.getMessage();
                if (msg != null) {
                    // last space to avoid matching other settings:
                    if (msg.startsWith("sun.java2d.renderer ")) {
                        isMarlin.set(msg.contains("MarlinRenderingEngine"));
                    }
                    if (msg.startsWith("sun.java2d.renderer.clip.runtime.enable")) {
                        isClipRuntime.set(msg.contains("true"));
                    }
                }

                final Throwable th = record.getThrown();
                // detect any Throwable:
                if (th != null) {
                    System.out.println("Test failed:\n" + record.getMessage());
                    th.printStackTrace(System.out);

                    throw new RuntimeException("Test failed: ", th);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });

        // enable Marlin logging & internal checks:
        System.setProperty("sun.java2d.renderer.log", "true");
        System.setProperty("sun.java2d.renderer.useLogger", "true");

        // fix subpixel accuracy:
        System.setProperty("sun.java2d.renderer.subPixel_log2_X", "3");
        System.setProperty("sun.java2d.renderer.subPixel_log2_Y", "3");

        // disable static clipping setting:
        System.setProperty("sun.java2d.renderer.clip", "false");
        System.setProperty("sun.java2d.renderer.clip.runtime.enable", "true");

        // enable subdivider:
        System.setProperty("sun.java2d.renderer.clip.subdivider", "true");

        // disable min length check: always subdivide curves at clip edges
        System.setProperty("sun.java2d.renderer.clip.subdivider.minLength", "-1");

        // If any curve, increase curve accuracy:
        // curve length max error:
        System.setProperty("sun.java2d.renderer.curve_len_err", "1e-4");

        // cubic min/max error:
        System.setProperty("sun.java2d.renderer.cubic_dec_d2", "1e-3");
        System.setProperty("sun.java2d.renderer.cubic_inc_d1", "1e-4"); // or disabled ~ 1e-6

        // quad max error:
        System.setProperty("sun.java2d.renderer.quad_dec_d2", "5e-4");
    }

    private static void resetOptions() {
        NUM_TESTS = 5000;

        // shape settings:
        SHAPE_MODE = ShapeMode.NINE_LINE_POLYS;

        USE_DASHES = false;
        USE_VAR_STROKE = false;
    }

    /**
     * Test
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("---------------------------------------");
        System.out.println("ClipShapeTest: image = " + TESTW + " x " + TESTH);

        resetOptions();

        boolean runSlowTests = false;

        for (String arg : args) {
            if ("-slow".equals(arg)) {
                runSlowTests = true;
            } else if ("-doDash".equals(arg)) {
                USE_DASHES = true;
            } else if ("-doVarStroke".equals(arg)) {
                USE_VAR_STROKE = true;
            } else {
                // shape mode:
                if (arg.equalsIgnoreCase("-poly")) {
                    SHAPE_MODE = ShapeMode.NINE_LINE_POLYS;
                } else if (arg.equalsIgnoreCase("-bigpoly")) {
                    SHAPE_MODE = ShapeMode.FIFTY_LINE_POLYS;
                } else if (arg.equalsIgnoreCase("-quad")) {
                    SHAPE_MODE = ShapeMode.FOUR_QUADS;
                } else if (arg.equalsIgnoreCase("-cubic")) {
                    SHAPE_MODE = ShapeMode.TWO_CUBICS;
                } else if (arg.equalsIgnoreCase("-mixed")) {
                    SHAPE_MODE = ShapeMode.MIXED;
                }
            }
        }

        System.out.println("Shape mode: " + SHAPE_MODE);

        // adjust image comparison thresholds:
        switch (SHAPE_MODE) {
            case TWO_CUBICS:
                // Define uncertainty for curves:
/*
Diff Pixels [Worst(All Test setups)][n: 293] sum: 16014 avg: 54.655 [4 | 160] {
            4 ..     8[n: 35] sum: 140 avg: 4.0 [4 | 4]
            8 ..    16[n: 32] sum: 320 avg: 10.0 [8 | 12]
           16 ..    32[n: 47] sum: 1068 avg: 22.723 [16 | 31]
           32 ..    64[n: 78] sum: 3566 avg: 45.717 [32 | 63]
           64 ..   128[n: 77] sum: 7453 avg: 96.792 [64 | 127]
          128 ..   256[n: 24] sum: 3467 avg: 144.458 [131 | 160] }
Differences [Diff Pixels [All Test setups]]:
NbPixels [All Test setups][n: 101819] sum: 250541 avg: 2.46 [0 | 175]

DASH: Diff Pixels [Worst(All Test setups)][n: 95] sum: 6255 avg: 65.842 [3 | 255] {
            2 ..     4[n: 1] sum: 3 avg: 3.0 [3 | 3]
            4 ..     8[n: 33] sum: 132 avg: 4.0 [4 | 4]
            8 ..    16[n: 10] sum: 88 avg: 8.8 [8 | 12]
           16 ..    32[n: 9] sum: 176 avg: 19.555 [16 | 28]
           32 ..    64[n: 8] sum: 363 avg: 45.375 [36 | 63]
           64 ..   128[n: 13] sum: 1285 avg: 98.846 [68 | 119]
          128 ..   256[n: 21] sum: 4208 avg: 200.38 [131 | 255] }
Differences [Diff Pixels [All Test setups]]:
NbPixels [All Test setups][n: 81430] sum: 3771 avg: 0.046 [0 | 42]

*/
                THRESHOLD_DELTA = 32;
                THRESHOLD_NBPIX = (USE_DASHES) ? 45 : 200;
                break;
            case FOUR_QUADS:
            case MIXED:
                // Define uncertainty for quads:
                // curve subdivision causes curves to be smaller
                // then curve offsets are different (more accurate)
/*
NbPixels [Worst(All Test setups)][n: 1] sum: 381 avg: 381.0 [381 | 381]
Diff Pixels [Worst(All Test setups)][n: 759] sum: 61342 avg: 80.819 [3 | 255] {
            2 ..     4[n: 3] sum: 9 avg: 3.0 [3 | 3]
            4 ..     8[n: 99] sum: 396 avg: 4.0 [4 | 4]
            8 ..    16[n: 76] sum: 800 avg: 10.526 [8 | 12]
           16 ..    32[n: 65] sum: 1460 avg: 22.461 [16 | 28]
           32 ..    64[n: 121] sum: 5621 avg: 46.454 [32 | 60]
           64 ..   128[n: 193] sum: 17740 avg: 91.917 [64 | 127]
          128 ..   256[n: 202] sum: 35316 avg: 174.831 [128 | 255] }
Differences [Diff Pixels [All Test setups]]:
NbPixels [All Test setups][n: 90621] sum: 2819936 avg: 31.117 [0 | 392]

DASH: Diff Pixels [Worst(All Test setups)][n: 87] sum: 3169 avg: 36.425 [4 | 96] {
            4 ..     8[n: 30] sum: 120 avg: 4.0 [4 | 4]
            8 ..    16[n: 7] sum: 60 avg: 8.571 [8 | 12]
           16 ..    32[n: 9] sum: 144 avg: 16.0 [16 | 16]
           32 ..    64[n: 15] sum: 776 avg: 51.733 [48 | 63]
           64 ..   128[n: 26] sum: 2069 avg: 79.576 [64 | 96] }
Differences [Diff Pixels [All Test setups]]:
NbPixels [All Test setups][n: 72710] sum: 66 avg: 0.0 [0 | 22]
*/
                THRESHOLD_DELTA = 64;
                THRESHOLD_NBPIX = (USE_DASHES) ? 25 : 420;
                break;
            default:
                // Define uncertainty for lines:
                // float variant have higher uncertainty
/*
DASH: Diff Pixels [Worst(All Test setups)][n: 7] sum: 8 avg: 1.142 [1 | 2] {
            1 ..     2[n: 6] sum: 6 avg: 1.0 [1 | 1]
            2 ..     4[n: 1] sum: 2 avg: 2.0 [2 | 2] }
*/
                THRESHOLD_DELTA = 2;
                THRESHOLD_NBPIX = 4; // very low
        }

        // TODO: define one more threshold on total result (total sum) ?

        System.out.println("THRESHOLD_DELTA: " + THRESHOLD_DELTA);
        System.out.println("THRESHOLD_NBPIX: " + THRESHOLD_NBPIX);

        if (runSlowTests) {
            NUM_TESTS = 10000; // or 100000 (very slow)
            USE_VAR_STROKE = true;
        }

        System.out.println("NUM_TESTS: " + NUM_TESTS);

        if (USE_DASHES) {
            System.out.println("USE_DASHES: enabled.");
        }
        if (USE_VAR_STROKE) {
            System.out.println("USE_VAR_STROKE: enabled.");
        }

        System.out.println("---------------------------------------");

        final DiffContext allCtx = new DiffContext("All Test setups");
        final DiffContext allWorstCtx = new DiffContext("Worst(All Test setups)");

        int failures = 0;
        final long start = System.nanoTime();
        try {
            if (TEST_STROKER) {
                final float[][] dashArrays = (USE_DASHES) ?
// small
//                        new float[][]{new float[]{1f, 2f}}
// normal
                        new float[][]{new float[]{13f, 7f}}
// large (prime)
//                        new float[][]{new float[]{41f, 7f}}
// none
                        : new float[][]{null};

                System.out.println("dashes: " + Arrays.deepToString(dashArrays));

                final float[] strokeWidths = (USE_VAR_STROKE)
                                                ? new float[5] :
                                                  new float[]{10f};

                int nsw = 0;
                if (USE_VAR_STROKE) {
                    for (float width = 0.25f; width < 110f; width *= 5f) {
                        strokeWidths[nsw++] = width;
                    }
                } else {
                    nsw = 1;
                }

                System.out.println("stroke widths: " + Arrays.toString(strokeWidths));

                // Stroker tests:
                for (int w = 0; w < nsw; w++) {
                    final float width = strokeWidths[w];

                    for (float[] dashes : dashArrays) {

                        for (int cap = 0; cap <= 2; cap++) {

                            for (int join = 0; join <= 2; join++) {

                                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, false, width, cap, join, dashes));
                                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, true, width, cap, join, dashes));
                            }
                        }
                    }
                }
            }

            if (TEST_FILLER) {
                // Filler tests:
                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, false, Path2D.WIND_NON_ZERO));
                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, true, Path2D.WIND_NON_ZERO));

                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, false, Path2D.WIND_EVEN_ODD));
                failures += paintPaths(allCtx, allWorstCtx, new TestSetup(SHAPE_MODE, true, Path2D.WIND_EVEN_ODD));
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        System.out.println("main: duration= " + (1e-6 * (System.nanoTime() - start)) + " ms.");

        allWorstCtx.dump();
        allCtx.dump();

        if (!isMarlin.get()) {
            throw new RuntimeException("Marlin renderer not used at runtime !");
        }
        if (!isClipRuntime.get()) {
            throw new RuntimeException("Marlin clipping not enabled at runtime !");
        }
        if (DO_FAIL && (failures != 0)) {
            throw new RuntimeException("Clip test failures : " + failures);
        }
    }

    static int paintPaths(final DiffContext allCtx, final DiffContext allWorstCtx, final TestSetup ts) throws IOException {
        final long start = System.nanoTime();

        if (FIXED_SEED) {
            // Reset seed for random numbers:
            RANDOM.setSeed(SEED);
        }

        System.out.println("paintPaths: " + NUM_TESTS
                + " paths (" + SHAPE_MODE + ") - setup: " + ts);

        final boolean fill = !ts.isStroke();
        final Path2D p2d = new Path2D.Double(ts.windingRule);

        final BufferedImage imgOn = newImage(TESTW, TESTH);
        final Graphics2D g2dOn = initialize(imgOn, ts);

        final BufferedImage imgOff = newImage(TESTW, TESTH);
        final Graphics2D g2dOff = initialize(imgOff, ts);

        final BufferedImage imgDiff = newImage(TESTW, TESTH);

        final DiffContext testSetupCtx = new DiffContext("Test setup");
        final DiffContext testWorstCtx = new DiffContext("Worst");
        final DiffContext testWorstThCtx = new DiffContext("Worst(>threshold)");

        int nd = 0;
        try {
            final DiffContext testCtx = new DiffContext("Test");
            final DiffContext testThCtx = new DiffContext("Test(>threshold)");
            BufferedImage diffImage;

            for (int n = 0; n < NUM_TESTS; n++) {
                genShape(p2d, ts);

                // Runtime clip setting OFF:
                paintShape(p2d, g2dOff, fill, false);

                // Runtime clip setting ON:
                paintShape(p2d, g2dOn, fill, true);

                /* compute image difference if possible */
                diffImage = computeDiffImage(testCtx, testThCtx, imgOn, imgOff, imgDiff);

                // Worst (total)
                if (testCtx.isDiff()) {
                    if (testWorstCtx.isWorse(testCtx, false)) {
                        testWorstCtx.set(testCtx);
                    }
                    if (testWorstThCtx.isWorse(testCtx, true)) {
                        testWorstThCtx.set(testCtx);
                    }
                    // accumulate data:
                    testSetupCtx.add(testCtx);
                }
                if (diffImage != null) {
                    nd++;

                    testThCtx.dump();
                    testCtx.dump();

                    if (nd < MAX_SHOW_FRAMES) {
                        if (SHOW_DETAILS) {
                            paintShapeDetails(g2dOff, p2d);
                            paintShapeDetails(g2dOn, p2d);
                        }

                        if (nd < MAX_SAVE_FRAMES) {
                            if (DUMP_SHAPE) {
                                dumpShape(p2d);
                            }

                            final String testName = "Setup_" + ts.id + "_test_" + n;

                            saveImage(imgOff, OUTPUT_DIR, testName + "-off.png");
                            saveImage(imgOn, OUTPUT_DIR, testName + "-on.png");
                            saveImage(imgDiff, OUTPUT_DIR, testName + "-diff.png");
                        }
                    }
                }
            }
        } finally {
            g2dOff.dispose();
            g2dOn.dispose();

            if (nd != 0) {
                System.out.println("paintPaths: " + NUM_TESTS + " paths - "
                        + "Number of differences = " + nd
                        + " ratio = " + (100f * nd) / NUM_TESTS + " %");
            }

            if (testWorstCtx.isDiff()) {
                testWorstCtx.dump();
                if (testWorstThCtx.isDiff() && testWorstThCtx.histPix.sum != testWorstCtx.histPix.sum) {
                    testWorstThCtx.dump();
                }
                if (allWorstCtx.isWorse(testWorstThCtx, true)) {
                    allWorstCtx.set(testWorstThCtx);
                }
            }
            testSetupCtx.dump();

            // accumulate data:
            allCtx.add(testSetupCtx);
        }
        System.out.println("paintPaths: duration= " + (1e-6 * (System.nanoTime() - start)) + " ms.");
        return nd;
    }

    private static void paintShape(final Path2D p2d, final Graphics2D g2d,
                                   final boolean fill, final boolean clip) {
        reset(g2d);

        setClip(g2d, clip);

        if (fill) {
            g2d.fill(p2d);
        } else {
            g2d.draw(p2d);
        }
    }

    private static Graphics2D initialize(final BufferedImage img,
                                         final TestSetup ts) {
        final Graphics2D g2d = (Graphics2D) img.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
// Test normalize:
                RenderingHints.VALUE_STROKE_NORMALIZE
//                RenderingHints.VALUE_STROKE_PURE
        );

        if (ts.isStroke()) {
            g2d.setStroke(createStroke(ts));
        }
        g2d.setColor(Color.BLACK);

        return g2d;
    }

    private static void reset(final Graphics2D g2d) {
        // Disable antialiasing:
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, TESTW, TESTH);
    }

    private static void setClip(final Graphics2D g2d, final boolean clip) {
        // Enable antialiasing:
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Enable or Disable clipping:
        System.setProperty("sun.java2d.renderer.clip.runtime", (clip) ? "true" : "false");
    }

    static void genShape(final Path2D p2d, final TestSetup ts) {
        p2d.reset();

        /*
            Test closed path:
            0: moveTo + (draw)To + closePath
            1: (draw)To + closePath (closePath + (draw)To sequence)
        */
        final int end  = (ts.closed) ? 2 : 1;

        for (int p = 0; p < end; p++) {
            if (p <= 0) {
                p2d.moveTo(randX(), randY());
            }

            switch (ts.shapeMode) {
                case MIXED:
                case FIVE_LINE_POLYS:
                case NINE_LINE_POLYS:
                case FIFTY_LINE_POLYS:
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    if (ts.shapeMode == ShapeMode.FIVE_LINE_POLYS) {
                        // And an implicit close makes 5 lines
                        break;
                    }
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    p2d.lineTo(randX(), randY());
                    if (ts.shapeMode == ShapeMode.NINE_LINE_POLYS) {
                        // And an implicit close makes 9 lines
                        break;
                    }
                    if (ts.shapeMode == ShapeMode.FIFTY_LINE_POLYS) {
                        for (int i = 0; i < 41; i++) {
                            p2d.lineTo(randX(), randY());
                        }
                        // And an implicit close makes 50 lines
                        break;
                    }
                case TWO_CUBICS:
                    p2d.curveTo(randX(), randY(), randX(), randY(), randX(), randY());
                    p2d.curveTo(randX(), randY(), randX(), randY(), randX(), randY());
                    if (ts.shapeMode == ShapeMode.TWO_CUBICS) {
                        break;
                    }
                case FOUR_QUADS:
                    p2d.quadTo(randX(), randY(), randX(), randY());
                    p2d.quadTo(randX(), randY(), randX(), randY());
                    p2d.quadTo(randX(), randY(), randX(), randY());
                    p2d.quadTo(randX(), randY(), randX(), randY());
                    if (ts.shapeMode == ShapeMode.FOUR_QUADS) {
                        break;
                    }
                default:
            }

            if (ts.closed) {
                p2d.closePath();
            }
        }
    }

    static final float POINT_RADIUS = 2f;
    static final float LINE_WIDTH = 1f;

    static final Stroke OUTLINE_STROKE = new BasicStroke(LINE_WIDTH);
    static final int COLOR_ALPHA = 128;
    static final Color COLOR_MOVETO = new Color(255, 0, 0, COLOR_ALPHA);
    static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, COLOR_ALPHA);
    static final Color COLOR_LINETO_EVEN = new Color(0, 255, 0, COLOR_ALPHA);

    static final Ellipse2D.Float ELL_POINT = new Ellipse2D.Float();

    private static void paintShapeDetails(final Graphics2D g2d, final Shape shape) {

        final Stroke oldStroke = g2d.getStroke();
        final Color oldColor = g2d.getColor();

        setClip(g2d, false);

        if (SHOW_OUTLINE) {
            g2d.setStroke(OUTLINE_STROKE);
            g2d.setColor(COLOR_LINETO_ODD);
            g2d.draw(shape);
        }

        final float[] coords = new float[6];
        float px, py;

        int nMove = 0;
        int nLine = 0;
        int n = 0;

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    if (SHOW_POINTS) {
                        g2d.setColor(COLOR_MOVETO);
                    }
                    break;
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_QUADTO:
                case PathIterator.SEG_CUBICTO:
                    if (SHOW_POINTS) {
                        g2d.setColor((nLine % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    }
                    nLine++;
                    break;
                case PathIterator.SEG_CLOSE:
                    continue;
                default:
                    System.out.println("unsupported segment type= " + type);
                    continue;
            }
            px = coords[0];
            py = coords[1];

            if (SHOW_INFO) {
                System.out.println("point[" + (n++) + "|seg=" + type + "]: " + px + " " + py);
            }

            if (SHOW_POINTS) {
                ELL_POINT.setFrame(px - POINT_RADIUS, py - POINT_RADIUS,
                        POINT_RADIUS * 2f, POINT_RADIUS * 2f);
                g2d.fill(ELL_POINT);
            }
        }
        if (SHOW_INFO) {
            System.out.println("Path moveTo=" + nMove + ", lineTo=" + nLine);
            System.out.println("--------------------------------------------------");
        }

        g2d.setStroke(oldStroke);
        g2d.setColor(oldColor);
    }

    private static void dumpShape(final Shape shape) {
        final float[] coords = new float[6];

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            final int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    System.out.println("p2d.moveTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_LINETO:
                    System.out.println("p2d.lineTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_QUADTO:
                    System.out.println("p2d.quadTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ");");
                    break;
                case PathIterator.SEG_CUBICTO:
                    System.out.println("p2d.curveTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ", " + coords[4] + ", " + coords[5] + ");");
                    break;
                case PathIterator.SEG_CLOSE:
                    System.out.println("p2d.closePath();");
                    break;
                default:
                    System.out.println("// Unsupported segment type= " + type);
            }
        }
        System.out.println("--------------------------------------------------");
    }

    static double randX() {
        return RANDOM.nextDouble() * RANDW + OFFW;
    }

    static double randY() {
        return RANDOM.nextDouble() * RANDH + OFFH;
    }

    private static BasicStroke createStroke(final TestSetup ts) {
        return new BasicStroke(ts.strokeWidth, ts.strokeCap, ts.strokeJoin, 10.0f, ts.dashes, 0.0f);
    }

    private final static class TestSetup {

        static final AtomicInteger COUNT = new AtomicInteger();

        final int id;
        final ShapeMode shapeMode;
        final boolean closed;
        // stroke
        final float strokeWidth;
        final int strokeCap;
        final int strokeJoin;
        final float[] dashes;
        // fill
        final int windingRule;

        TestSetup(ShapeMode shapeMode, final boolean closed,
                  final float strokeWidth, final int strokeCap, final int strokeJoin, final float[] dashes) {
            this.id = COUNT.incrementAndGet();
            this.shapeMode = shapeMode;
            this.closed = closed;
            this.strokeWidth = strokeWidth;
            this.strokeCap = strokeCap;
            this.strokeJoin = strokeJoin;
            this.dashes = dashes;
            this.windingRule = Path2D.WIND_NON_ZERO;
        }

        TestSetup(ShapeMode shapeMode, final boolean closed, final int windingRule) {
            this.id = COUNT.incrementAndGet();
            this.shapeMode = shapeMode;
            this.closed = closed;
            this.strokeWidth = 0f;
            this.strokeCap = this.strokeJoin = -1; // invalid
            this.dashes = null;
            this.windingRule = windingRule;
        }

        boolean isStroke() {
            return this.strokeWidth > 0f;
        }

        @Override
        public String toString() {
            if (isStroke()) {
                return "TestSetup{id=" + id + ", shapeMode=" + shapeMode + ", closed=" + closed
                        + ", strokeWidth=" + strokeWidth + ", strokeCap=" + getCap(strokeCap) + ", strokeJoin=" + getJoin(strokeJoin)
                        + ((dashes != null) ? ", dashes: " + Arrays.toString(dashes) : "")
                        + '}';
            }
            return "TestSetup{id=" + id + ", shapeMode=" + shapeMode + ", closed=" + closed
                    + ", fill"
                    + ", windingRule=" + getWindingRule(windingRule) + '}';
        }

        private static String getCap(final int cap) {
            switch (cap) {
                case BasicStroke.CAP_BUTT:
                    return "CAP_BUTT";
                case BasicStroke.CAP_ROUND:
                    return "CAP_ROUND";
                case BasicStroke.CAP_SQUARE:
                    return "CAP_SQUARE";
                default:
                    return "";
            }

        }

        private static String getJoin(final int join) {
            switch (join) {
                case BasicStroke.JOIN_MITER:
                    return "JOIN_MITER";
                case BasicStroke.JOIN_ROUND:
                    return "JOIN_ROUND";
                case BasicStroke.JOIN_BEVEL:
                    return "JOIN_BEVEL";
                default:
                    return "";
            }

        }

        private static String getWindingRule(final int rule) {
            switch (rule) {
                case PathIterator.WIND_EVEN_ODD:
                    return "WIND_EVEN_ODD";
                case PathIterator.WIND_NON_ZERO:
                    return "WIND_NON_ZERO";
                default:
                    return "";
            }
        }
    }

    // --- utilities ---
    private static final int DCM_ALPHA_MASK = 0xff000000;

    public static BufferedImage newImage(final int w, final int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    public static BufferedImage computeDiffImage(final DiffContext testCtx,
                                                 final DiffContext testThCtx,
                                                 final BufferedImage tstImage,
                                                 final BufferedImage refImage,
                                                 final BufferedImage diffImage) {

        final int[] aRefPix = ((DataBufferInt) refImage.getRaster().getDataBuffer()).getData();
        final int[] aTstPix = ((DataBufferInt) tstImage.getRaster().getDataBuffer()).getData();
        final int[] aDifPix = ((DataBufferInt) diffImage.getRaster().getDataBuffer()).getData();

        // reset diff contexts:
        testCtx.reset();
        testThCtx.reset();

        int ref, tst, dg, v;
        for (int i = 0, len = aRefPix.length; i < len; i++) {
            ref = aRefPix[i];
            tst = aTstPix[i];

            // grayscale diff:
            dg = (r(ref) + g(ref) + b(ref)) - (r(tst) + g(tst) + b(tst));

            // max difference on grayscale values:
            v = (int) Math.ceil(Math.abs(dg / 3.0));
            if (v <= THRESHOLD_DELTA) {
                aDifPix[i] = 0;
            } else {
                aDifPix[i] = toInt(v, v, v);
                testThCtx.add(v);
            }

            if (v != 0) {
                testCtx.add(v);
            }
        }

        testCtx.addNbPix(testThCtx.histPix.count);

        if (!testThCtx.isDiff() || (testThCtx.histPix.count <= THRESHOLD_NBPIX)) {
            return null;
        }

        return diffImage;
    }

    static void saveImage(final BufferedImage image, final File resDirectory, final String imageFileName) throws IOException {
        final Iterator<ImageWriter> itWriters = ImageIO.getImageWritersByFormatName("PNG");
        if (itWriters.hasNext()) {
            final ImageWriter writer = itWriters.next();

            final ImageWriteParam writerParams = writer.getDefaultWriteParam();
            writerParams.setProgressiveMode(ImageWriteParam.MODE_DISABLED);

            final File imgFile = new File(resDirectory, imageFileName);

            if (!imgFile.exists() || imgFile.canWrite()) {
                System.out.println("saveImage: saving image as PNG [" + imgFile + "]...");
                imgFile.delete();

                // disable cache in temporary files:
                ImageIO.setUseCache(false);

                final long start = System.nanoTime();

                // PNG uses already buffering:
                final ImageOutputStream imgOutStream = ImageIO.createImageOutputStream(new FileOutputStream(imgFile));

                writer.setOutput(imgOutStream);
                try {
                    writer.write(null, new IIOImage(image, null, null), writerParams);
                } finally {
                    imgOutStream.close();

                    final long time = System.nanoTime() - start;
                    System.out.println("saveImage: duration= " + (time / 1000000l) + " ms.");
                }
            }
        }
    }

    static int r(final int v) {
        return (v >> 16 & 0xff);
    }

    static int g(final int v) {
        return (v >> 8 & 0xff);
    }

    static int b(final int v) {
        return (v & 0xff);
    }

    static int clamp127(final int v) {
        return (v < 128) ? (v > -127 ? (v + 127) : 0) : 255;
    }

    static int toInt(final int r, final int g, final int b) {
        return DCM_ALPHA_MASK | (r << 16) | (g << 8) | b;
    }

    /* stats */
    static class StatInteger {

        public final String name;
        public long count = 0l;
        public long sum = 0l;
        public long min = Integer.MAX_VALUE;
        public long max = Integer.MIN_VALUE;

        StatInteger(String name) {
            this.name = name;
        }

        void reset() {
            count = 0l;
            sum = 0l;
            min = Integer.MAX_VALUE;
            max = Integer.MIN_VALUE;
        }

        void add(int val) {
            count++;
            sum += val;
            if (val < min) {
                min = val;
            }
            if (val > max) {
                max = val;
            }
        }

        void add(long val) {
            count++;
            sum += val;
            if (val < min) {
                min = val;
            }
            if (val > max) {
                max = val;
            }
        }

        void add(StatInteger stat) {
            count += stat.count;
            sum += stat.sum;
            if (stat.min < min) {
                min = stat.min;
            }
            if (stat.max > max) {
                max = stat.max;
            }
        }

        public final double average() {
            return ((double) sum) / count;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(128);
            toString(sb);
            return sb.toString();
        }

        public final StringBuilder toString(final StringBuilder sb) {
            sb.append(name).append("[n: ").append(count);
            sb.append("] sum: ").append(sum).append(" avg: ").append(trimTo3Digits(average()));
            sb.append(" [").append(min).append(" | ").append(max).append("]");
            return sb;
        }

    }

    final static class Histogram extends StatInteger {

        static final int BUCKET = 2;
        static final int MAX = 20;
        static final int LAST = MAX - 1;
        static final int[] STEPS = new int[MAX];
        static final int BUCKET_TH;

        static {
            STEPS[0] = 0;
            STEPS[1] = 1;

            for (int i = 2; i < MAX; i++) {
                STEPS[i] = STEPS[i - 1] * BUCKET;
            }
//            System.out.println("Histogram.STEPS = " + Arrays.toString(STEPS));

            if (THRESHOLD_DELTA % 2 != 0) {
                throw new IllegalStateException("THRESHOLD_DELTA must be odd");
            }

            BUCKET_TH = bucket(THRESHOLD_DELTA);
        }

        static int bucket(int val) {
            for (int i = 1; i < MAX; i++) {
                if (val < STEPS[i]) {
                    return i - 1;
                }
            }
            return LAST;
        }

        private final StatInteger[] stats = new StatInteger[MAX];

        public Histogram(String name) {
            super(name);
            for (int i = 0; i < MAX; i++) {
                stats[i] = new StatInteger(String.format("%5s .. %5s", STEPS[i], ((i + 1 < MAX) ? STEPS[i + 1] : "~")));
            }
        }

        @Override
        final void reset() {
            super.reset();
            for (int i = 0; i < MAX; i++) {
                stats[i].reset();
            }
        }

        @Override
        final void add(int val) {
            super.add(val);
            stats[bucket(val)].add(val);
        }

        @Override
        final void add(long val) {
            add((int) val);
        }

        void add(Histogram hist) {
            super.add(hist);
            for (int i = 0; i < MAX; i++) {
                stats[i].add(hist.stats[i]);
            }
        }

        boolean isWorse(Histogram hist, boolean useTh) {
            boolean worst = false;
            if (!useTh && (hist.sum > sum)) {
                worst = true;
            } else {
                long sumLoc = 0l;
                long sumHist = 0l;
                // use running sum:
                for (int i = MAX - 1; i >= BUCKET_TH; i--) {
                    sumLoc += stats[i].sum;
                    sumHist += hist.stats[i].sum;
                }
                if (sumHist > sumLoc) {
                    worst = true;
                }
            }
            /*
            System.out.println("running sum worst:");
            System.out.println("this ? " + toString());
            System.out.println("worst ? " + hist.toString());
             */
            return worst;
        }

        @Override
        public final String toString() {
            final StringBuilder sb = new StringBuilder(2048);
            super.toString(sb).append(" { ");

            for (int i = 0; i < MAX; i++) {
                if (stats[i].count != 0l) {
                    sb.append("\n        ").append(stats[i].toString());
                }
            }

            return sb.append(" }").toString();
        }
    }

    /**
     * Adjust the given double value to keep only 3 decimal digits
     * @param value value to adjust
     * @return double value with only 3 decimal digits
     */
    static double trimTo3Digits(final double value) {
        return ((long) (1e3d * value)) / 1e3d;
    }

    static final class DiffContext {

        public final Histogram histPix;

        public final StatInteger nbPix;

        DiffContext(String name) {
            histPix = new Histogram("Diff Pixels [" + name + "]");
            nbPix = new StatInteger("NbPixels [" + name + "]");
        }

        void reset() {
            histPix.reset();
            nbPix.reset();
        }

        void dump() {
            if (isDiff()) {
                System.out.println("Differences [" + histPix.name + "]:\n"
                        + ((nbPix.count != 0) ? (nbPix.toString()+"\n") : "")
                        + histPix.toString()
                );
            } else {
                System.out.println("No difference for [" + histPix.name + "].");
            }
        }

        void add(int val) {
            histPix.add(val);
        }

        void add(DiffContext ctx) {
            histPix.add(ctx.histPix);
            nbPix.add(ctx.nbPix);
        }

        void addNbPix(long val) {
            nbPix.add(val);
        }

        void set(DiffContext ctx) {
            reset();
            add(ctx);
        }

        boolean isWorse(DiffContext ctx, boolean useTh) {
            return histPix.isWorse(ctx.histPix, useTh);
        }

        boolean isDiff() {
            return histPix.sum != 0l;
        }
    }
}
