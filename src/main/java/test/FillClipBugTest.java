package test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class FillClipBugTest {

    public static void main(String[] args) {

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

        JFrame frame = new JFrame("JFrame Example");
        JPanel panel = new MyPanel();
        panel.setPreferredSize(new Dimension(MyPanel.max, MyPanel.max));
        panel.setBorder(new LineBorder(Color.BLUE));
        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.pack();
        frame.setVisible(true);
    }

    static class MyPanel extends JPanel {

        final static int max = 250 - 1;
        final static int decalX = 200;
        final static int decalY = 200;

        private Path2D.Double rect1 = createRectOutsideBounds(400, 300);
        private Path2D.Double rect2 = createRectOverBounds();

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            final int w = getWidth();
            final int h = getHeight();

            final BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            final Graphics2D g2d = (Graphics2D) bi.getGraphics();

            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, w, h);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.blue);
            g2d.fill(rect1);

            g2d.setColor(Color.red);
            g2d.fill(rect2);

            g.drawImage(bi, 0, 0, this);

            g2d.dispose();
        }

        private static Path2D.Double createRectOutsideBounds(final int w, final int h) {
            final Path2D.Double shape = new Path2D.Double();
            shape.moveTo(-decalX, -decalY);
            shape.lineTo(w + decalX, -decalY); // go right
            shape.lineTo(w + decalX, h + decalY); // go down
            shape.lineTo(-decalX, h + decalY); // go left

            if (false) {
                shape.closePath();
            }
            return shape;
        }

        private static Path2D.Double createRectOverBounds() {
            final Path2D.Double shape = new Path2D.Double();
            shape.moveTo(0 + decalX, 50 + decalY);
            shape.lineTo(0 + decalX, 0 + decalY); // go up
            shape.lineTo(50 + decalX, 0 + decalY); // go right
            shape.lineTo(50 + decalX, 50 + decalY); // go down
            shape.lineTo(0 + decalX, 50 + decalY); // go left

            if (false) {
                shape.closePath();
            }
            return shape;
        }
    }

}
