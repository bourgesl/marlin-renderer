/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.marlin.pipe.BlendComposite;
import org.marlin.pipe.MarlinCompositor;
import org.marlin.pisces.MarlinProperties;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin / ductus renderers
 */
public class LineWidthColorGridTest {

    private final static int N = 20;

    private final static String FILE_NAME = "LineWidthColorGridTest_";

    private final static Color[] COLORS = new Color[]{
        Color.BLACK,
        Color.GRAY,
        Color.WHITE,
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.MAGENTA,
        Color.YELLOW,
        Color.CYAN
    };
    /* PINK, ORANGE */

    private final static int MARGIN = 8;
    private final static int WIDTH = 128;
    private final static int HALF_HEIGHT = 52;

    public static void main(String[] args) {
        final int square = Math.max(2 * MARGIN + WIDTH, 2 * HALF_HEIGHT + 3 * MARGIN);

        final int nColors = COLORS.length;

        final int width = square * nColors;
        final int height = width;

        System.out.println("LineWidthColorGridTest: size = " + width + " x " + height);

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setClip(0, 0, width, height);
        g2d.setBackground(Color.LIGHT_GRAY); // unused color
        g2d.clearRect(0, 0, width, height);

        final AffineTransform ident = g2d.getTransform();

        for (int n = 0; n < N; n++) {

            final long start = System.nanoTime();

            for (int j = 0; j < nColors; j++) {

                g2d.setTransform(ident);

                for (int i = 0; i < nColors; i++) {

                    // test same color FG = BG if any colorspace bug does not return Identity
                    if (false && (i == j)) {
                        continue;
                    }

                    g2d.setTransform(ident);
                    g2d.translate(i * square, j * square);

                    paint(g2d, COLORS[i], COLORS[j]);
                }
            }

            final long time = System.nanoTime() - start;
            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }

        try {

            final File file = new File(FILE_NAME + MarlinProperties.getSubPixel_Log2_X()
                    + "x" + MarlinProperties.getSubPixel_Log2_Y()
                    + ((MarlinCompositor.ENABLE_COMPOSITOR) ? ("_comp"
                            + BlendComposite.getBlendingMode()) : "_REF") + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());;
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final Color color, final Color bgcolor) {
        g2d.setBackground(bgcolor);
        {
            g2d.clearRect(MARGIN - 1, MARGIN - 1, WIDTH + 2, HALF_HEIGHT + 2);

            g2d.setColor(color);
            double off = MARGIN;

            for (int i = 1; i <= 22; i++) {
                final double width = i / 8.0;

                // Add 0.5 to be at pixel center:
                setPathLine(PATH, off + 0.5, MARGIN + 0.5, off + 1.5, MARGIN + HALF_HEIGHT - 0.5, 0.5 * width);
                g2d.fill(PATH);
                off = Math.ceil(off + width + 4.0);
            }
        }
        {
            final int y = MARGIN + HALF_HEIGHT + MARGIN;

            g2d.setBackground(bgcolor);
            g2d.clearRect(MARGIN - 1, y - 1, WIDTH + 2, HALF_HEIGHT + 2);

            final int w = 8;
            final int ns = WIDTH / w;

            for (int i = 0; i < ns; i++) {
                final int a = (int) Math.round((255.0 * i) / (ns - 1));
                //System.out.println("alpha: " + a);
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
                // TODO: fix fill rect (ref rountines)
                final double x1 = MARGIN + (i * w);
                final double x2 = x1 + w;
                // not at pixel center ??
                setPathRect(PATH, x1, y, x2, y + HALF_HEIGHT); // 2px wide
                g2d.fill(PATH);
                // g2d.fillRect(x, y, 1, HALF_HEIGHT);
            }
        }
    }

    private static final Path2D.Double PATH = new Path2D.Double();

    private static void setPathLine(final Path2D.Double path,
                                    final double x1, final double y1,
                                    final double x2, final double y2,
                                    final double width) {

        double dx = x2 - x1;
        double dy = y2 - y1;
        double d = Math.sqrt(dx * dx + dy * dy);

        dx = width * (y2 - y1) / d;
        dy = width * (x2 - x1) / d;

        path.reset();
        path.moveTo(x1 - dx, y1 + dy);
        path.lineTo(x2 - dx, y2 + dy);
        path.lineTo(x2 + dx, y2 - dy);
        path.lineTo(x1 + dx, y1 - dy);
        path.closePath();
    }

    private static void setPathRect(final Path2D.Double path,
                                    final double x1, final double y1,
                                    final double x2, final double y2) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y1);
        path.lineTo(x2, y2);
        path.lineTo(x1, y2);
        path.closePath();
    }
}
