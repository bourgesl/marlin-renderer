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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Simple Line rendering test using GeneralPath

INFO: sun.java2d.renderer.clip             = true
INFO: sun.java2d.renderer.clip.subdivider  = false

- RECT_SIZE: 3.072E7
dashes: [1.0]
--- Test [0] ---
paint: duration= 1541.1364509999999 ms.
paint: duration= 1887.649172 ms.
paint: duration= 2193.6667429999998 ms.
paint: duration= 2178.339183 ms.
paint: duration= 2176.393356 ms.
paint: duration= 2176.082841 ms.
paint: duration= 2183.116776 ms.
paint: duration= 2187.393376 ms.
paint: duration= 2183.929231 ms.

INFO: sun.java2d.renderer.clip             = true
INFO: sun.java2d.renderer.clip.subdivider  = true

- RECT_SIZE: 3.072E7
dashes: [1.0]
--- Test [990] ---
paint: duration= 2.086631 ms.
paint: duration= 2.091678 ms.
paint: duration= 2.089244 ms.
paint: duration= 2.092831 ms.
paint: duration= 2.086923 ms.
paint: duration= 2.09877 ms.
paint: duration= 2.088752 ms.
paint: duration= 2.090345 ms.
paint: duration= 2.087383 ms.
paint: duration= 2.086769 ms.


- CURVE RADIUS: 1.8432E7
dashes: [1.0]
--- Test [0] ---
INFO: AAShapePipe: overriding JDK implementation: marlin-renderer TILE patch enabled.
paint: duration= 5220.190052999999 ms.
paint: duration= 5295.282114 ms.

--- Test [990] ---
paint: duration= 2.642565 ms.
paint: duration= 2.651068 ms.
paint: duration= 2.635271 ms.
paint: duration= 2.6239589999999997 ms.
paint: duration= 2.6342779999999997 ms.
paint: duration= 2.637479 ms.
paint: duration= 2.634371 ms.
paint: duration= 2.63426 ms.
paint: duration= 2.636333 ms.
paint: duration= 2.628594 ms.
*/
public class ShapeOutlineTest {

    private final static int N = 1000;

    private final static boolean DO_FILL = true;
    private final static boolean DO_DRAW = true;
    private final static boolean DO_DRAW_DASHED = true;

    private final static boolean DO_QUAD = false;
    private final static boolean DO_CIRCLE = true;

    private final static double CIRCLE_RADIUS = 1843200.0 * 10.0;

    private final static double RECT_SIZE = 900.0 * 1024 * 30; // * 5;

    private final static double sqrt2 = Math.sqrt(2);

    private final static BasicStroke PLAIN = new BasicStroke(10);
    private final static BasicStroke DASHED = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 20f,
// large:
            new float[]{10f, 5f}, 0f
// small:
//            new float[]{1f}, 0f
// complex:
//            new float[]{0.17f, 0.39f, 0.137f, 0.487f}, 0.733333f
    );

    public static void main(String[] args) {

        final int size = 1000;

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

        System.out.println("ShapeOutlineTest: size = " + size);

        if (DO_CIRCLE || DO_QUAD) {
            System.out.println("CURVE RADIUS: " + CIRCLE_RADIUS);
        } else {
            System.out.println("RECT_SIZE: " + RECT_SIZE);
        }

        if (DO_DRAW_DASHED) {
            System.out.println("dashes: "+Arrays.toString(DASHED.getDashArray()));
        }

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setClip(0, 0, size, size);

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        // RECT:
        // old: 4009.9072039999996 ms.
        // fix: 4094.81685
        // with inlined variables: old = 3770 ms
        // Ductus 1.8: 35934.7 ms
        // DMarlinRenderingEngine: 4131.276773 ms.
        // CIRCLE:
        // old: 2696.341058 ms.
        // fix: 2442.098762 ms.
        // CPU fixed without clipping:  4357.567511 ms.
        // Stroker clipping: 700 ms.
        for (int i = 0; i < N; i++) {
            if (i % 10 == 0) {
                System.out.println("--- Test [" + i + "] ---");
            }
            final long start = System.nanoTime();

            paint(g2d, size);

            final long time = System.nanoTime() - start;

            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }

        try {
            final File file = new File("ShapeOutlineTest-"
                    + (DO_CIRCLE ? "circle" : (DO_QUAD ? "quad" : "rect")) + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final double size) {
        final Shape path = createPath(size);

        if (DO_FILL) {
            g2d.setColor(Color.BLUE);
            g2d.fill(path);
        }

        if (DO_DRAW) {
            g2d.setColor(Color.GREEN);
            g2d.setStroke(PLAIN);
            g2d.draw(path);
        }

        if (DO_DRAW_DASHED) {
            g2d.setColor(Color.RED);
            g2d.setStroke(DASHED);
            g2d.draw(path);
        }
    }

    private static Shape createPath(final double size) {
        if (DO_CIRCLE) {
            final double c = (0.5 * size - CIRCLE_RADIUS / sqrt2);

            return new Ellipse2D.Double(
                    c - CIRCLE_RADIUS,
                    c - CIRCLE_RADIUS,
                    2.0 * CIRCLE_RADIUS,
                    2.0 * CIRCLE_RADIUS
            );
        } else if (DO_QUAD) {
            final double c = (0.5 * size - CIRCLE_RADIUS / sqrt2);
            final double half = 0.5 * size;

            final Path2D p = new Path2D.Double();
            p.moveTo(-size, -size);
            p.quadTo(half, 5 * size, size, -size);
            p.closePath();
            return p;
        } else {
            final double half = 0.5 * size;

            final Path2D p = new Path2D.Double();
            p.moveTo(half, half);
            p.lineTo(-RECT_SIZE, -RECT_SIZE);
            p.lineTo(0.0, -RECT_SIZE * 2.0);
            p.lineTo(RECT_SIZE, -RECT_SIZE);
            p.lineTo(half, half);
            p.closePath();
            return p;
        }
    }
}
