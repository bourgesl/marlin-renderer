/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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

/**
 *
 * @author llooo
 */
public class LineTests {
    
    public static float off = 0.3f;

    public static void main(String[] args) {
        final float lineStroke = 2f;
        final int size = 600;
        
        System.out.println("LineTests: size = " + size);
        
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setClip(0, 0, size, size);
        g2d.setStroke(new BasicStroke(lineStroke));

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, size, size);

        g2d.setColor(Color.RED);
        paint(g2d, size - 2f * lineStroke);

        try {
            final File file = new File("LinesTest-" + off + ".png");
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
        
        for (float angle = 1f / 3f; angle <=90f; angle += 1f) {
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