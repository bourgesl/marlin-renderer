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

package sun.java2d.marlin;

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
 *
 */
public class TextTransformTest {

    private final static String TEXT = generateText();

    /**
     */
    public static void main(String[] args) {
        final int size = 500;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(0.75f));

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));

        g2d.setColor(Color.BLUE);
        paint(g2d, size);

        try {
            final File file = new File("TextTransformTest.png");
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

        g2d.translate(0, (int)(size - 50));

        final Font font = g2d.getFont();

        final int incDeg = 2;

        for (int i = 0, len = 360 / incDeg; i <= len; i += 1) {
            final FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), true, true);
            final GlyphVector gv = font.createGlyphVector(fontRenderContext, TEXT);

            final int numGlyphs = gv.getNumGlyphs();
            for (int index = 0; index < numGlyphs; index++) {
                g2d.draw(gv.getGlyphOutline(index));
            }
//            g2d.draw(gv.getOutline());

//            g2d.rotate(angInc);
            g2d.translate(0, 1);
        }
    }

    private static String generateText() {
        final int len = 255;
        StringBuilder sb = new StringBuilder(len);

        for (int i = 33; i < len; i++) {
            sb.append(Character.toChars(i));
        }

        final String text = sb.toString();
//        System.out.println("text: " + text);
        return text;
    }

}
