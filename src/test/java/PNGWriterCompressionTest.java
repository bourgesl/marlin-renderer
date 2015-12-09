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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * @test @bug 6488522
 * @summary Check the compression support in PNGImageWriter
 * @run main PNGWriterCompressionTest
 */
public class PNGWriterCompressionTest {

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        final BufferedImage image
            = new BufferedImage(250, 250, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                                 RenderingHints.VALUE_RENDER_QUALITY);

            g2d.setColor(Color.red);
            g2d.draw(new Rectangle2D.Float(10, 10, 100, 100));
            g2d.setColor(Color.blue);
            g2d.fill(new Rectangle2D.Float(12, 12, 98, 98));
            g2d.setColor(Color.green);
            g2d.setFont(new Font(Font.SERIF, Font.BOLD, 14));

            for (int i = 0; i < 15; i++) {
                g2d.drawString("Testing PNG Compression ...", 20, 20 + i * 16);
            }

            final Iterator<ImageWriter> itWriters
                = ImageIO.getImageWritersByFormatName("PNG");

            final ImageWriter writer;
            final ImageWriteParam writerParams;

            if (itWriters.hasNext()) {
                writer = itWriters.next();

                writerParams = writer.getDefaultWriteParam();
                writerParams.setProgressiveMode(ImageWriteParam.MODE_DISABLED);

                writerParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writerParams.setCompressionQuality(0.5f);
            } else {
                throw new RuntimeException("Unable to get PNG writer !");
            }

            testCompression(image, writer, writerParams);
        } catch (IOException ioe) {
            throw new RuntimeException("IO failure", ioe);
        }
        finally {
            g2d.dispose();
        }
    }

    private static void testCompression(final BufferedImage image,
                                        final ImageWriter writer,
                                        final ImageWriteParam writerParams)
        throws IOException
    {
        // Test Compression modes:
        writerParams.setCompressionMode(ImageWriteParam.MODE_DEFAULT);
        testSavePNG(image, writer, writerParams, "default");

        writerParams.setCompressionMode(ImageWriteParam.MODE_DISABLED);
        testSavePNG(image, writer, writerParams, "disabled");

        writerParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        for (int i = 0; i <= 10; i++) {
            float quality = 0.1f * i;
            writerParams.setCompressionQuality(quality);
            testSavePNG(image, writer, writerParams,
                            String.format("explicit-%.1f", quality));
        }
    }

    private static void testSavePNG(final BufferedImage image,
                                    final ImageWriter writer,
                                    final ImageWriteParam writerParams,
                                    final String mode) throws IOException
    {
        final File imgFile = new File("PNGWriterCompressionTest-"
            + mode + ".png");

        System.out.println("Writing file: " + imgFile.getAbsolutePath());

        // PNG uses already buffering:
        final ImageOutputStream imgOutStream
            = ImageIO.createImageOutputStream(new FileOutputStream(imgFile));

        writer.setOutput(imgOutStream);

        writer.write(null, new IIOImage(image, null, null), writerParams);

        imgOutStream.close();
    }
}
