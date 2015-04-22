package test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import sun.java2d.pipe.RenderingEngine;

public class TestDashNPE {

    public static void main(String[] args) {
        final String renderer = RenderingEngine.getInstance().getClass().getSimpleName();
        System.out.println("Testing renderer = " + renderer);
        
        GeneralPath shape = new GeneralPath();
        int[] pointTypes = {0, 0, 1, 1, 0, 1, 1, 0};
        double[] xpoints = {428, 420, 400, 400, 400, 400, 420, 733};
        double[] ypoints = {180, 180, 180, 160, 30, 10, 10, 10};
        shape.moveTo(xpoints[0], ypoints[0]);
        for (int i = 1; i < pointTypes.length; i++) {
            if (pointTypes[i] == 1 && i < pointTypes.length - 1) {
                shape.quadTo(xpoints[i], ypoints[i], xpoints[i + 1], ypoints[i + 1]);
            } else {
                shape.lineTo(xpoints[i], ypoints[i]);
            }
        }

        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        Color color = new Color(124, 0, 124, 255);
        g2.setColor(color);
        Stroke stroke = new BasicStroke(1.0f, 0, 0, 10.0f, new float[]{9, 6}, 0.0f);
        g2.setStroke(stroke);
        g2.draw(shape);

    }
}
