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
package test;

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
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Simple Stroke clipping BUG 0.8.1 test using GeneralPath
 *
 * run it with -Dsun.java2d.renderer.clip=true/false
 */
public class StrokeClipBugTest {

    private final static int N = 1;

    private final static boolean DO_FILL = true;
    private final static boolean DO_DRAW = true;

    public static void main(String[] args) {

        // fix subpixel accuracy:
        System.setProperty("sun.java2d.renderer.subPixel_log2_X", "3");
        System.setProperty("sun.java2d.renderer.subPixel_log2_Y", "3");

        // enable subdivider:
        System.setProperty("sun.java2d.renderer.clip.subdivider", "true");

        // disable min length check: always subdivide curves at clip edges
        System.setProperty("sun.java2d.renderer.clip.subdivider.minLength", "-1");

        // If any curve, increase curve accuracy:
        // curve length max error:
        System.setProperty("sun.java2d.renderer.curve_len_err", "1e-2"); // 1e-4

        // cubic min/max error:
        System.setProperty("sun.java2d.renderer.cubic_dec_d2", "1e-3");
        System.setProperty("sun.java2d.renderer.cubic_inc_d1", "1e-4"); // or disabled ~ 1e-6

        // quad max error:
        System.setProperty("sun.java2d.renderer.quad_dec_d2", "5e-4");

        final float lineStroke = 10f; // 10f
        final int size = 500;

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

        System.out.println("StrokeClipBugTest: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        if (false) {
            g2d.setClip(0, 0, size, size);
        }
        g2d.setStroke(
                new BasicStroke(lineStroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f,
//                        new float[]{225f, 3f},
//                        new float[]{2.3f, 2.1f},
//                        new float[]{15f, 8f},
                        new float[]{13f, 7f},
                        0f)
        );

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.translate(100, 100);
        g2d.scale(3, 3);

        for (int i = 0; i < N; i++) {
            final long start = System.nanoTime();

            paint(g2d, size);

            final long time = System.nanoTime() - start;

            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }

        final String clipFlag = System.getProperty("sun.java2d.renderer.clip");
        try {
            final File file = new File("StrokeClipBugTest-clip-" + clipFlag + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final float size) {
        final Shape path = createPath();

        if (false) {
            Shape strokedShape = g2d.getStroke().createStrokedShape(path);
            dumpShape(strokedShape);
        }

        if (DO_FILL) {
            g2d.setColor(Color.BLUE);
            g2d.fill(path);
        }

        if (DO_DRAW) {
            g2d.setColor(Color.RED);
            g2d.draw(path);
        }

        System.out.println("--- Paint done ---");

        paintShapeDetails(g2d, path);

        if (false) {
            paintShapeDetails(g2d, path);

            g2d.scale(1 / 3.0, 1 / 3.0);
            g2d.translate(-100.0, -100.0);

            g2d.scale(2.0, 2.0);

            final Path2D p = new Path2D.Double();
            p.moveTo(137.99162163078213, 215.31530031548442);
            p.lineTo(137.9916216307821, 215.31530031548442);
            p.curveTo(140.96480170244465, 217.67197028188173, 142.91226416681286, 219.13209303809683, 143.3888779966452, 219.48960846838207);
            p.lineTo(143.3888779966486, 219.48960846838463);
            p.curveTo(143.4273969690626, 219.51850214736496, 143.47658855505745, 219.55539174103652, 143.51530067825502, 219.5845531038936);
            p.lineTo(143.5153006782631, 219.58455310389968);
            p.curveTo(143.46586137713834, 219.54731109079324, 143.65837162270583, 219.69098506140625, 143.84391250127183, 219.83805271473273);
            p.lineTo(143.84391250124386, 219.83805271471056);
            p.curveTo(129.26589657997678, 208.28289242735886, 158.69212050199732, 225.2583001991717, 146.2837459131644, 240.91273318355934);
            p.lineTo(146.28374591260217, 240.91273318426866);
            p.curveTo(134.42973364867052, 255.8677814244974, 110.51330706217857, 232.80291671540607, 125.73177079246064, 243.74541378723924);
            p.lineTo(125.73177079246578, 243.74541378724294);
            p.curveTo(125.68816245972596, 243.7140581877705, 125.63098863773665, 243.6727468902147, 125.5836954856681, 243.6385844815311);
            p.lineTo(125.5836954856695, 243.63858448153212);
            p.curveTo(124.56908174427625, 242.90567394853926, 117.22543351663492, 237.59713301975947, 100.14312150301227, 226.1159478525718);
            p.lineTo(116.87783232112191, 201.2171636687157);
            p.curveTo(134.22882847186196, 212.8789340123871, 141.8504089554286, 218.38058942101273, 143.15052191207818, 219.3197315212375);
            p.lineTo(143.15052191207957, 219.3197315212385);
            p.curveTo(143.21176148329675, 219.36396818485736, 143.23530854479367, 219.38096595934138, 143.245312025409, 219.38815873906483);
            p.lineTo(143.24531202541414, 219.38815873906853);
            p.curveTo(158.51712228456174, 230.36901344319952, 134.6272007777896, 207.3229407513003, 122.77354453321898, 222.2775398367112);
            p.lineTo(122.77354453265673, 222.27753983742053);
            p.curveTo(110.36525926338487, 237.93186013605123, 139.7899266103851, 254.90594412922965, 125.20871915326066, 243.34825409375628);
            p.lineTo(125.20871915323269, 243.3482540937341);
            p.curveTo(125.38469894664945, 243.48774322114747, 125.55297499379267, 243.61301557669884, 125.4649223430482, 243.5466866056479);
            p.lineTo(125.46492234305629, 243.54668660565397);
            p.curveTo(125.45665901824972, 243.54046194543938, 125.43759375810981, 243.5261510195533, 125.3871037737306, 243.48827769872082);
            p.lineTo(125.38710377373401, 243.48827769872338);
            p.curveTo(124.78557634645946, 243.03706264047364, 122.61018580507809, 241.40456937488315, 119.35642828276848, 238.825501694506);
            p.closePath();

            paintShapeDetails(g2d, p);
        }
    }

    private static Shape createPath() {
        final Path2D p2d = new Path2D.Double();

        if (true) {
            if (false) {
//                p2d.moveTo(41.963448, -0.15559639);
                p2d.moveTo(41.963448, 10.15559639);
//                p2d.curveTo(-98.73395, -13.014817, 88.59084, 91.56239, -30.084085, 16.486795);
                p2d.curveTo(-98.73395, -13.014817, 88.59084, 91.56239, -30.084085, 13.486795);
//            p2d.curveTo(165.38525, -96.85821, -48.13681, 5.6739063, -59.77725, 46.52815);
//            p2d.closePath();
                /*
            p2d.moveTo(41.963448, -0.15559639);
            p2d.curveTo(-89.15587, 67.898766, 154.3097, -52.749317, 101.07614, -97.15763);
            p2d.curveTo(95.605484, 97.46206, 49.60154, 24.028162, -45.875294, -82.60816);
            p2d.closePath();
                 */
            } else {
                if (true) {
                    p2d.moveTo(96.86455, -36.84057);
                    p2d.curveTo(-82.93064, 10.936657, 102.61017, 67.47694, 124.05019, -99.10604);
                    p2d.curveTo(51.755047, 117.672005, 84.98797, 24.218536, 88.920746, -0.5595716);
                    p2d.closePath();
                    p2d.curveTo(150.39502, 2.7396517, 117.0494, 67.51341, 146.4544, 31.122284);
                    p2d.curveTo(133.16582, 132.94756, 34.05092, 75.62286, 143.7332, 67.26044);
                    p2d.closePath();

                } else {
                    p2d.moveTo(96.86455, -36.84057);
                    p2d.curveTo(-82.93064, 10.936657, 102.61017, 67.47694, 124.05019, -99.10604);
                    p2d.curveTo(51.755047, 117.672005, 84.98797, 24.218536, 88.920746, -0.5595716);
                }
            }
        } else {
            // outside top
            p2d.moveTo(100, -100);

            p2d.lineTo(100.0, 50);
            p2d.lineTo(300.0, 200);

            p2d.closePath();
        }
        return p2d;
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

    static final boolean SHOW_OUTLINE = true;
    static final boolean SHOW_POINTS = true;
    static final boolean SHOW_INFO = false;

    static final float POINT_RADIUS = 0.75f;
    static final float LINE_WIDTH = 0.25f;

    static final Stroke OUTLINE_STROKE = new BasicStroke(LINE_WIDTH);
    static final int COLOR_ALPHA = 128;
    static final Color COLOR_MOVETO = new Color(255, 0, 0, COLOR_ALPHA);
    static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, COLOR_ALPHA);
    static final Color COLOR_LINETO_EVEN = new Color(0, 255, 0, COLOR_ALPHA);

    static final Ellipse2D.Float ELL_POINT = new Ellipse2D.Float();

    private static void paintShapeDetails(final Graphics2D g2d, final Shape shape) {

        final Stroke oldStroke = g2d.getStroke();
        final Color oldColor = g2d.getColor();

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
}
