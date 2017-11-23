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
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Simple Line Clipping rendering test
 */
public class LineClipTest {

    private final static int N = 100;

    static final int NUM_OFFSCREEN = 10000;
    static boolean OMIT_OFFSCREEN = false;

    static boolean DO_FILL = true;

    public static void main(String[] args) {

        final int size = 200;

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

        System.out.println("LineClipTest: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setClip(0, 0, size, size);
        g2d.setStroke(new BasicStroke(1f));
//        g2d.setStroke( new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER) );

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.setColor(Color.BLUE);
        final Shape path = createPath(size);
        Shape fillPath = null;

        for (int i = 0; i < N; i++) {
            final long start = System.nanoTime();

            if (DO_FILL) {
                if (fillPath == null) {
                    fillPath = g2d.getStroke().createStrokedShape(path);
                }
                g2d.fill(fillPath);
            } else {
                g2d.draw(path);
            }

            final long time = System.nanoTime() - start;

            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }

        try {
            final File file = new File("LineClipTest-"
                    + (OMIT_OFFSCREEN ? "-noOffscreen" : "") + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static Shape createPath(final float size) {
        final Path2D p = new Path2D.Float();

        if (OMIT_OFFSCREEN) {
            p.moveTo(-100, 100);
        } else {
            p.moveTo(-500, 100);

            for (int i = 0; i < NUM_OFFSCREEN; i++) {
                double x = Math.random() * 400 - 500;
                double y = Math.random() * 200;
                p.lineTo(x, y);
            }
            p.lineTo(-100, 100);
        }
        p.lineTo(50, 150);
        p.lineTo(150, 50);
        return p;
    }
}
