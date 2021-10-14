/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Simple ellipse rendering test
 *
 * @test
 * @summary TODO
 * @bug TODO
 */
public class EllipseFillParallelTest {

    private final static int N = 100;

    private static Toolkit DEF_TOOLKIT = null;

    static {
        System.setProperty("sun.java2d.opengl", "true");

        // First display which renderer is tested:
        // JDK9 only:
        System.setProperty("sun.java2d.renderer.verbose", "true");

        try {
            DEF_TOOLKIT = Toolkit.getDefaultToolkit();
        } catch (IllegalArgumentException iae) {
            System.err.println("Unable to load awt toolkit");
        }
    }

    private final static GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

    public static void main(String[] args) {

        final int size = 1000;

        System.out.println("EllipseFillParallelTest: size = " + size);

        final VolatileImage image = gc.createCompatibleVolatileImage(size, size, Transparency.TRANSLUCENT);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            g2d.setBackground(Color.WHITE);
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke());

            for (int i = 0; i < N; i++) {
                // Clear:
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.clearRect(0, 0, size, size);

                // use AA to upload Marlin masks (ssbo):
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                final long start = System.nanoTime();

                g2d.fillOval(0, 0, size, size);

                sync();

                final long time = System.nanoTime() - start;

                System.out.println("paint: duration= " + (1e-6 * time) + " ms.");

                final BufferedImage bimg = image.getSnapshot();

                if (!checkImage(bimg)) {
                    try {
                        final File file = new File("EllipseFillParallelTest-" + i + ".png");

                        System.out.println("Writing file: " + file.getAbsolutePath());
                        ImageIO.write(bimg, "PNG", file);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private static boolean checkImage(final BufferedImage bimg) {
        final int[] pixels = ((DataBufferInt) bimg.getRaster().getDataBuffer()).getData();

        final int white_rgb = Color.WHITE.getRGB();
        final int blue_rgb = Color.BLUE.getRGB();

        int cb = 0;
        int cw = 0;
        int co = 0;

        for (int i = 0; i < pixels.length; i++) {
            final int pix = pixels[i];

            if (pix == blue_rgb) {
                cb++;
            } else if (pix == white_rgb) {
                cw++;
            } else {
                co++;
            }
        }
        final boolean ok = (Math.abs(cb - COUNT_BLUE) < THRESHOLD) && (Math.abs(cw - COUNT_WHITE) < THRESHOLD);
        
        if (!ok) {
            System.out.println("Counts: blue = " + cb + " white = " + cw + " others = " + co);
            // REF: Counts: blue = 783538 white = 212988 others = 3474
        }
        return ok;
    }

    private final static int COUNT_BLUE = 783672;
    private final static int COUNT_WHITE = 212684;

    private final static int THRESHOLD = 350;

    public static void sync() {
        if (DEF_TOOLKIT != null) {
            DEF_TOOLKIT.sync();
        }
    }

}
