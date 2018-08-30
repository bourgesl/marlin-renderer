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
 * Simple Dashed Line Clipping rendering test

- Diagonal line:

 * Float variant:

LINE_HZ = false + CLIPPING OFF:

paint: duration= 586.4380219999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

Larger: float => while 1 (precision issue in decrement ?)

---
LINE_HZ = false + CLIPPING ON:

paint: duration= 153.18715799999998 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png



>> Dasher Fix Only (kahan sum):

LINE_HZ = false + CLIPPING OFF:

paint: duration= 629.438667 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

paint: duration= 6233.9366279999995 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

---
LINE_HZ = false + CLIPPING ON:

paint: duration= 20.089544 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2147483.0-dashed-stroked.png

paint: duration= 184.397379 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

paint: duration= 1749.1042699999998 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

paint: duration= 17595.900109 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748365E9-dashed-stroked.png



---
 * Double variant:

LINE_HZ = false + CLIPPING OFF:

paint: duration= 74.80852999999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2147483.0-dashed-stroked.png

paint: duration= 692.7280599999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

paint: duration= 6910.535513 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

No test for MAX-2.14748365E9

---
LINE_HZ = false + CLIPPING ON:

paint: duration= 18.354069 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2147483.0-dashed-stroked.png

paint: duration= 155.210102 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

paint: duration= 1532.940877 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

paint: duration= 15235.051479 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748365E9-dashed-stroked.png



LINE_HZ = true + CLIPPING OFF:

paint: duration= 121.60999899999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2147483.0-dashed-stroked.png

paint: duration= 1275.035922 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

[GC (Allocation Failure)  1270757K->1259627K(2095616K), 0,0703327 secs]
[Full GC (Ergonomics)  1259627K->420695K(2095616K), 0,0455937 secs]
paint: duration= 14317.566315 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

No test for MAX-2.14748365E9

---
LINE_HZ = true + CLIPPING ON:

paint: duration= 9.73142 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2147483.0-dashed-stroked.png

paint: duration= 110.56740599999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.1474836E7-dashed-stroked.png

paint: duration= 1078.545259 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748368E8-dashed-stroked.png

paint: duration= 10815.384732999999 ms.
Writing file: /home/marlin/branches/marlin-renderer-unsafe/DashedLineClipTest-MAX-2.14748365E9-dashed-stroked.png

 */
public class DashedLineClipTest {

    private final static int N = 10;

    final static boolean DO_DASHED = true;
    final static boolean DO_FILL = false;

    final static boolean LINE_HZ = false;

    final static float MAX = Integer.MAX_VALUE / 10;

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

        System.out.println("DashedLineClipTest: size = " + size);

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setClip(0, 0, size, size);
        g2d.setStroke(createStroke());

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
            final File file = new File("DashedLineClipTest-MAX-" + MAX
                    + (DO_DASHED ? "-dashed" : "") + (DO_FILL ? "-filled" : "-stroked") + ".png");

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

        if (LINE_HZ) {
            p.moveTo(-5.5, 16);

            // Mimics JDK-9:
            // g2d.getDeviceConfiguration().getBounds()
            p.lineTo(MAX, 16);

        } else {
            p.moveTo(-5.5, -0.5);

            // Mimics JDK-9:
            // g2d.getDeviceConfiguration().getBounds()
            p.lineTo(MAX, MAX);
        }

        return p;
    }

    private static BasicStroke createStroke() {
        return new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                ((DO_DASHED) ? new float[]{4f} : null), 0.0f);
    }

}
