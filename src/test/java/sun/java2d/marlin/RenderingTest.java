package sun.java2d.marlin;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class RenderingTest {

    public static void main(String[] args) {
        BufferedImage bi = new BufferedImage(1024, 1024, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = bi.createGraphics();
        g.draw(new Rectangle2D.Float(10, 10, 100, 100));
        g.fill(new Rectangle2D.Float(10, 10, 100, 100));
        g.dispose();
    }
}
