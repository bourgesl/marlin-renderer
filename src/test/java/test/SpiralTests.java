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
import java.awt.geom.Path2D;
import static java.awt.geom.Path2D.WIND_NON_ZERO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import sun.java2d.pipe.RenderingEngine;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin /
 * ductus renderers
 */
public class SpiralTests {

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

        StrokerTest.main(args);

        final boolean useDashes = false;
        final float lineStroke = 2f;

        BasicStroke stroke = createStroke(lineStroke, useDashes);

        final int size = 4096;

        System.out.println("SpiralTests: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        g2d.setClip(0, 0, size, size);
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.setStroke(stroke);
        g2d.setColor(Color.BLUE);
        final long start = System.nanoTime();

        paint(g2d, size - 10f);

        final long time = System.nanoTime() - start;

        System.out.println("paint: duration= " + (1e-6 * time) + " ms.");

        try {
            final File file = new File("SpiralTests-" + renderer + "-dash-" + useDashes + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final float size) {

        final Path2D.Float path = new Path2D.Float(WIND_NON_ZERO, 256 * 1024);
        path.moveTo(0f, 0f);

        final double halfSize = size / 2.0;
        g2d.translate(halfSize, halfSize);

        final double maxRadius = Math.sqrt(2.0) * halfSize;
        final double twoPi = 2.0 * Math.PI;
        final double stepCircle = 10.0;

        double r = 1.0;

        double angle, sa, sr;

        int n = 0;

        while (r < maxRadius) {
            // circle
            sa = twoPi / (2.0 * r);
            sr = stepCircle / (twoPi / sa);

            for (angle = 0.0; angle <= twoPi; angle += sa) {
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                path.lineTo(r * cos, r * sin);

                r += sr;
                n++;
            }
        }
        System.out.println("draw : " + n + " lines.");
        g2d.draw(path);
    }

    private static BasicStroke createStroke(final float width, final boolean useDashes) {
        final float[] dashes;

        if (useDashes) {
            dashes = new float[8192];

            float cur, step = 0.1f;
            cur = step;
            for (int i = 0; i < dashes.length; i++) {
                dashes[i] = cur;
                cur += step;
            }
        } else {
            dashes = null;
        }

        return new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f, dashes, 0.0f);
    }
}
