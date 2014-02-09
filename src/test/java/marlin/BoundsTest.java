package marlin;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 *
 * @author bourgesl
 */
public class BoundsTest {

    public static float off = 0.3f;

    /**
     * @test @bug 6887494
     *
     * @summary Verifies that no NullPointerException is thrown in Pisces Renderer under certain circumstances.
     *
     * @run main TestNPE
     */
    public static void main(String[] args) {
        final int size = 10;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        // Trigger exception in main thread.
        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setClip(0, 0, size, size);
        g2d.setStroke(new BasicStroke(0.5f));

//        for (off = 0f; off < 4f; off += 0.5f) {
        g2d.setBackground(Color.YELLOW);
        g2d.clearRect(0, 0, size, size);

        g2d.setColor(Color.RED);
        paint(g2d, -1f + size);

        try {
            ImageIO.write(image, "PNG", new File("BoundsTest-" + off + ".png"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
//        }

        g2d.dispose();
    }

    private static void paint(final Graphics2D g2d, final float size) {
        /* on boundaries */
        paintRect(g2d, 8f - off, 8f - off, 1f); // 8+1.75 + stroke / 2 = 10
        /* small */
        paintRect(g2d, 4f, 4f, 0.847f);
        /* inside */
        paintRect(g2d, 3f, 3f, 3f);
        /* boundaries (corners) */
        paintRect(g2d, -2f, -2f, 4f);
        paintRect(g2d, -2f, -2f + size, 4f);
        paintRect(g2d, -2f + size, -2f, 4f);
        paintRect(g2d, -2f + size, -2f + size, 4f);
    }

    private static void paintRect(final Graphics2D g2d, final float x0, final float y0, final float size) {
        final GeneralPath.Float path = new GeneralPath.Float();
        final float x = x0 + off;
        final float y = y0 + off;
        path.moveTo(x, y);
        path.lineTo(x + size, y);
        path.lineTo(x + size, y + size);
        path.lineTo(x, y + size);
        path.closePath();
        g2d.draw(path);
    }
}
