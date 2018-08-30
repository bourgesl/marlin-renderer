/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class LineBug {

    public static void main(String[] args) {
        try {
// Create image and get Graphics2D
            BufferedImage bi = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB); // RGB
            Graphics2D g2 = bi.createGraphics();

// LBO: added
            if ("true".equals(System.getProperty("useAA"))) {
                System.out.println("Use AA");
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }

// Fill white and set basic properties
            g2.setColor(Color.white);
            g2.fillRect(0, 0, 1000, 1000);
            g2.setColor(Color.black);
            g2.setStroke(new BasicStroke(1));

// Set transform and clip
            g2.setTransform(new AffineTransform(0.5, 0, 0, 0.5, 100, 100));
            g2.setClip(new Rectangle2D.Double(-1, 0, 2, 386));

// LBO: added
            if ("true".equals(System.getProperty("useShape"))) {
                System.out.println("Use Shape");
                GeneralPath path = new GeneralPath();
                path.moveTo(0, 0);
                path.lineTo(0, 385);
                g2.draw(path);
            } else {
// Draw the line
                g2.drawLine(0, 0, 0, 385);
            }

// Write the output image
            ImageIO.write(bi, "png", new File("linebug.png"));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
