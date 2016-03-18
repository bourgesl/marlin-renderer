/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import sun.java2d.pipe.RenderingEngine;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin /
 * ductus renderers
 */
public class StrokerTest {

    static final boolean SHOW_OUTLINE = true;
    static final boolean SHOW_POINTS = true;

    static final Stroke OUTLINE_STROKE = new BasicStroke(1f);
    static final Color COLOR_MOVETO = new Color(255, 0, 0, 160);
    static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, 160);
    static final Color COLOR_LINETO_EVEN = new Color(0, 255, 0, 160);

    public static void main(String[] args) {
        final int nSteps = 7;

        final float lineStroke = 20f;
        final float miterLimit = 5f;
        final float[] dashes = null; //new float[] {30, 45};
        final int height = 300;

        final float margin = 10f + lineStroke;
        final int width = height * nSteps;

//        final int cap = BasicStroke.CAP_BUTT; // No cap
        final int cap = BasicStroke.CAP_SQUARE; // No cap
        final int join = BasicStroke.JOIN_MITER; // mitter
//        final int join = BasicStroke.JOIN_ROUND; // round
//        final int join = BasicStroke.JOIN_BEVEL; // bevel

        final String renderer = RenderingEngine.getInstance().getClass().getSimpleName();
        System.out.println("Testing renderer = " + renderer);

        System.out.println("StrokerTest: width = " + width);
        System.out.println("StrokerTest: height = " + height);

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        g2d.setClip(0, 0, width, height);

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, width, height);

        final Stroke stroke = new BasicStroke(lineStroke, cap, join, miterLimit, dashes, 0.0f);

        g2d.translate(margin, margin);

        final long start = System.nanoTime();

        paint(g2d, stroke, height, margin, nSteps);

        final long time = System.nanoTime() - start;

        System.out.println("paint: duration= " + (1e-6 * time) + " ms.");

        try {
            final String simplifier = System.getProperty("sun.java2d.renderer.useSimplifier");

            final File file = new File("StrokerTest-" + renderer + "_norm-subpix_cap_" + cap
            + "_join_" + join + "_simplifier_" + simplifier + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());;
            ImageIO.write(image, "PNG", file);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final Stroke stroke,
                              final float size,
                              final float margin,
                              final int nSteps) {
        final float half = 0.5f * (size - 2f * margin);
        final double step = 2.0 * Math.PI / nSteps;

        final Path2D.Float path = new Path2D.Float();

        int ns = 0;

        for (double angle = -Math.PI / 4.0; angle <= (Math.PI * 7.0 / 4.0); angle += step, ns++) {
            System.out.println("--------------------------------------------------");
            System.out.println("Test angle=" + angle);

            path.reset();

            path.moveTo(0f, 0f);
            path.lineTo(half, half);
            path.lineTo(half + half * Math.cos(angle), half + half * Math.sin(angle));
            /*
             // CCW
             path.moveTo(size, 0f);
             path.lineTo(size, half);
             path.lineTo(size - 80, 0f);
             */

            g2d.setStroke(stroke);
            final Shape strokedShape = g2d.getStroke().createStrokedShape(path);

            System.out.println("createStrokedShape done -------------------------");

            g2d.setColor(Color.GRAY);
            g2d.fill(strokedShape);

            if (SHOW_OUTLINE) {
                g2d.setStroke(OUTLINE_STROKE);
                g2d.setColor(COLOR_LINETO_ODD);
                g2d.draw(strokedShape);
            }

            final float[] coords = new float[6];

            int nMove = 0;
            int nLine = 0;
            int n = 0;

            final Ellipse2D.Float ellipse = new Ellipse2D.Float();
            float px, py;

            for (final PathIterator it = strokedShape.getPathIterator(null);
            !it.isDone();
            it.next()) {
                int type = it.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        nMove++;
                        if (SHOW_POINTS) {
                            g2d.setColor(COLOR_MOVETO);
                        }
                        break;
                    case PathIterator.SEG_LINETO:
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

                System.out.println("point[" + (n++) + "|seg=" + type + "]: " + px + " " + py);

                if (SHOW_POINTS) {
                    ellipse.setFrame(px - 2.5f, py - 2.5f, 5f, 5f);
                    g2d.fill(ellipse);
                }
            }
            System.out.println("Stoked Path moveTo=" + nMove + ", lineTo=" + nLine);
            System.out.println("--------------------------------------------------");

            g2d.translate(size, 0.0);
        }
    }
}
