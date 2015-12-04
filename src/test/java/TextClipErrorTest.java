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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * @test
 * @summary Check the Stroker.drawBezApproxForArc() bug (stoke with round joins):
 * abs(cosext2) > 0.5 generates curves with NaN coordinates
 * @run main TextClipErrorTest
 * 
 * @author Martin JANDA
 */
public class TextClipErrorTest {

    public static void main(String[] args) {
        BufferedImage image = new BufferedImage(256, 256,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.red);
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Font font = g2d.getFont();
            FontRenderContext frc = new FontRenderContext(
                    new AffineTransform(), true, true);

            GlyphVector gv1 = font.createGlyphVector(frc, "\u00d6");

            g2d.setStroke(new BasicStroke(4.0f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            AffineTransform at1 = AffineTransform.getTranslateInstance(
                    -2091202.554154681, 5548.601436981691);
            g2d.draw(at1.createTransformedShape(gv1.getOutline()));

            GlyphVector gv2 = font.createGlyphVector(frc, "Test 2");

            AffineTransform at2 = AffineTransform.getTranslateInstance(
//                    -218.1810476789251, 85.12774919422463);
                    10, 50);            
            g2d.draw(at2.createTransformedShape(gv2.getOutline()));
            

            final File file = new File("TextClipErrorTest.png");
            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
            
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            g2d.dispose();
        }            
    }
}
