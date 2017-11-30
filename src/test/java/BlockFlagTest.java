/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * @test
 * @bug
 * @summary Verifies that Marlin block flag optimization handles
 * correctly pixels at block boundaries
 * @run main BlockFlagTest
 */
public class BlockFlagTest {

    static final boolean SAVE_IMAGE = false;

    public static void main(String argv[]) {
        Locale.setDefault(Locale.US);

        // initialize j.u.l Looger:
        final Logger log = Logger.getLogger("sun.java2d.marlin");
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                Throwable th = record.getThrown();
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
        System.setProperty("sun.java2d.renderer.doChecks", "true");

        testDraw();
    }

    private static void testDraw() {
        final int size = 400;

        final double margin = 20.0;
        final double max_px = size - 4.0 * margin;
        final double orig_x = 63.81;

        final BufferedImage image = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            final AffineTransform at = g2d.getTransform();

            g2d.setColor(Color.BLACK);
//            g2d.setStroke(new BasicStroke(1f / 8f)); // 1/8th pixel ie 1 subpixel

            final Path2D.Double path = new Path2D.Double();

            for (float off = 0f; off <= 64f; off += 0.05f) {
                final double x = orig_x + off;

                System.out.println("Test boundary at x = " + x);

                // clear previous image:
                g2d.setTransform(at);
                g2d.setBackground(Color.WHITE);
                g2d.clearRect(0, 0, size, size);

                // reset path:
                path.reset();

                path.moveTo(margin, 0);
                path.lineTo(margin, max_px);

                path.lineTo(x, max_px);
                path.lineTo(x, 0);

                path.lineTo(max_px, 0);
                path.lineTo(max_px, max_px);

                // close U shape:
                path.lineTo(max_px + margin, max_px + margin);
                path.lineTo(0, max_px + margin);
                path.lineTo(0, 0);
                path.closePath();

                g2d.translate(margin, margin);
                g2d.fill(path);

                if (SAVE_IMAGE) {
                    try {
                        final File file = new File("BlockFlagTest-draw-" + off + ".png");
                        System.out.println("Writing file: "
                                + file.getAbsolutePath());
                        ImageIO.write(image, "PNG", file);
                    } catch (IOException ex) {
                        System.out.println("Writing file failure:");
                        ex.printStackTrace();
                    }
                }
            } // loop
        } finally {
            g2d.dispose();
        }
    }
}
