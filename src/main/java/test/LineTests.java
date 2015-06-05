/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.marlin.pisces.MarlinRenderingEngine;
import sun.java2d.pipe.RenderingEngine;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin / ductus renderers
 */
public class LineTests {

    public static void main(String[] args) {
        final float lineStroke = 2f;
        final int size = 2600;
        
        System.out.println("Testing renderer = " + RenderingEngine.getInstance().getClass().getName());

        System.out.println("LineTests: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        
        g2d.setClip(0, 0, size, size);
        g2d.setStroke(new BasicStroke(lineStroke));

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.setColor(Color.RED);

        final long start = System.nanoTime();

        paint(g2d, size - 2f * lineStroke);

        final long time = System.nanoTime() - start;

        System.out.println("paint: duration= " + (1e-6 * time) + " ms.");

        try {
            final File file = new File("LinesTest-norm-subpix_lg_" + MarlinRenderingEngine.getSubPixel_Log2_X()
                    + "x" + MarlinRenderingEngine.getSubPixel_Log2_Y() + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());;
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final float size) {

        final Path2D.Float path = new Path2D.Float();

        for (float angle = 1f / 3f; angle <= 90f; angle += 1f) {
            double angRad = Math.toRadians(angle);

            double cos = Math.cos(angRad);
            double sin = Math.sin(angRad);

            path.reset();

            path.moveTo(5f * cos, 5f * sin);
            path.lineTo(size * cos, size * sin);

            g2d.draw(path);
        }
    }
}