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
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin /
 * ductus renderers
 */
public class StrokeClipTest {

    private final static int N = 1;

    private final static boolean DO_CLOSE_PATH = true;

    private final static boolean DO_CIRCLE = false;

    private final static boolean DO_FILL = true;
    private final static boolean DO_DRAW = false;

    private final static float CIRCLE_RADIUS = 100f;

    private final static double sqrt2 = Math.sqrt(2);

    public static void main(String[] args) {

        final float lineStroke = 4f;
        final int size = 400;

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

        System.out.println("StrokeClipTest: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setClip(0, 0, size, size);
        g2d.setStroke(
                new BasicStroke(lineStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 20f)
        );

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        for (int i = 0; i < N; i++) {
            final long start = System.nanoTime();

            paint(g2d, size);

            final long time = System.nanoTime() - start;

            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }

        try {
            final File file = new File("StrokeClipTest-"
                    + (DO_CIRCLE ? "circle" : "rect") + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final float size) {
        final AffineTransform tx = g2d.getTransform();
        final Shape path = createPath(size);

        for (int i = 500; i < 600; i += 1500) {
            g2d.setTransform(AffineTransform.getTranslateInstance(i - 10000, 0));

            if (DO_FILL) {
                g2d.setColor(Color.BLUE);
                g2d.fill(path);
            }

            if (DO_DRAW) {
                g2d.setColor(Color.RED);
                g2d.draw(path);
            }
        }
        g2d.setTransform(tx);
    }

    private static Shape createPath(final float size) {
        if (DO_CIRCLE) {
            return new Ellipse2D.Float(
                    -CIRCLE_RADIUS,
                    100,
                    2.0f * CIRCLE_RADIUS,
                    2.0f * CIRCLE_RADIUS
            );

        } else {
            final Path2D p = new Path2D.Float();
            p.moveTo(100, 100);
            p.lineTo(100.0, 50);
            p.lineTo(-100, 10);
            p.lineTo(-100, 100);
            p.lineTo(300.0, 200);
            p.lineTo(100, 300);
            p.lineTo(120, 80);

            if (DO_CLOSE_PATH) {
                p.closePath();
            }
            return p;
        }
    }
}
