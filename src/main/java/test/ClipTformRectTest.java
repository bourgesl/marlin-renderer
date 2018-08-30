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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ClipTformRectTest {

    static int width = 1064;
    static int height = 493;

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(
                new Runnable() {

            @Override
            public void run() {

                final JFrame frame = new JFrame();
                frame.getContentPane().add(new JPanel() {
                    private void drawRectangle(final Graphics2D graphics, final double x, final double y,
                                               final Color color) {
                        final GeneralPath path = new GeneralPath();
                        path.moveTo(x, y);
                        path.lineTo(x, y + 1000000);
                        path.lineTo(x + 1000000, y + 1000000);
                        path.lineTo(x + 1000000, y);
                        path.lineTo(x, y);
                        path.closePath();

                        System.out.println("rect: (" + x + ", " + y + ") to (" + (x + 1000000) + ", " + (y + 1000000) + ")");

                        final Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 32);
                        graphics.setPaint(fillColor);
                        graphics.fill(path);
                        graphics.setColor(color);
                        graphics.draw(path);
                    }

                    private void drawRectangles(final Graphics2D graphics, final Color color) {
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
                        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                        final AffineTransform transform = (AffineTransform) graphics.getTransform().clone();
                        final double unitPerPixel = 3.580027381163965E-4;
// ortho-scale only:
//                        final double unitPerPixel =   3.5800273811639657E-4;
                        final double unitPerPixelY = -3.5800273811639657E-4;
                        final AffineTransform modelToScreenTransform = new AffineTransform();
                        modelToScreenTransform
                                .concatenate(AffineTransform.getScaleInstance(unitPerPixel, unitPerPixelY));
// shear:
                        if (false) {
                            modelToScreenTransform
                                    .concatenate(AffineTransform.getShearInstance(1e-3, 0.623));
                        }
                        modelToScreenTransform.concatenate(
                                AffineTransform.getTranslateInstance(501923.5574694474, -1739426.9589435714));
                        graphics.transform(modelToScreenTransform);
                        final float strokeWidth = 1 / (float) unitPerPixel;
                        graphics.setStroke(new BasicStroke(strokeWidth));

                        System.out.println("strokeWidth: " + strokeWidth);

                        for (long y = 0; y < 2000000; y += 1000000) {
                            for (long x = 0; x < 2000000; x += 1000000) {
                                drawRectangle(graphics, x, y, color);
                            }
                        }
                        graphics.setTransform(transform);
                    }

                    @Override
                    protected void paintComponent(final Graphics g) {
                        super.paintComponent(g);

                        final Graphics2D componentGraphics = (Graphics2D) g;
                        componentGraphics.setPaint(Color.WHITE);
                        componentGraphics.fillRect(0, 0, width, height);
                        // drawRectangles(componentGraphics, Color.BLUE);

                        final BufferedImage image = new BufferedImage(width, height,
                                BufferedImage.TYPE_INT_ARGB_PRE);
                        final Graphics2D imageGraphics = (Graphics2D) image.getGraphics();

                        drawRectangles(imageGraphics, Color.RED);

                        componentGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                        componentGraphics.drawImage(image, 0, 0, width, height, null);
                    }
                });
                frame.setSize(width, height + 25);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }

        });
    }
}
