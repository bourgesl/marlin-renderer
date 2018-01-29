package test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class FillClipBugTest {

    private static final int PASS = 1;

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

        if (true) {
            createFrame("Test", new PanelPath(false));
            createFrame("Test Clip", new PanelPath(true));
        } else {
            createFrame("Test", new PanelRects());
            createFrame("Test", new PanelRect2());
        }
    }

    static void createFrame(final String title, final JPanel panelTest) {
        final JFrame frame = new JFrame(title);
        frame.add(panelTest);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.pack();
        frame.setVisible(true);
    }

    static final class PanelPath extends JPanel {

        final boolean showClip;
        final static int max = 100;

        private Path2D.Double path = createPath();

        PanelPath(final boolean showClip) {
            this.showClip = showClip;
            setPreferredSize(new Dimension(computeSize(), computeSize()));
            setBorder(new LineBorder(Color.GREEN));
        }

        private int computeSize() {
            return ((showClip) ? 3 : 1) * max;
        }

        @Override
        protected void paintComponent(Graphics g) {
            System.out.println("paintComponent() showClip: "+showClip+" ---");

            super.paintComponent(g);

            final int size = computeSize();

            final BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            final Graphics2D g2d = (Graphics2D) bi.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            final AffineTransform tx = g2d.getTransform();

            for (int n = 0; n < PASS; n++) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setBackground(Color.WHITE);
                g2d.clearRect(0, 0, size, size);

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (showClip) {
                    g2d.setColor(Color.RED);
                    g2d.drawRect(max, max, max, max);
                    g2d.translate(max, max);
                }

                setAttributes(g2d);
                // g2d.fill(path);
                g2d.draw(path);

                g2d.setTransform(tx);
            }

            g.drawImage(bi, 0, 0, this);

            g2d.dispose();
        }

        private void setAttributes(final Graphics2D g2d) {
            g2d.setColor(Color.GRAY);

            g2d.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f,
                    new float[]{1f, 2f}, 0.0f));
//                    new float[]{13f, 2f}, 0.0f));
        }

        private static Path2D.Double createPath() {
            final Path2D.Double p2d = new Path2D.Double();
            // ClipShapeTests dumped shape code:
            // --- begin of pasted code ---
p2d.moveTo(146.82729, 178.81085);
p2d.quadTo(196.73093, 186.10008, 199.25151, 94.648094);
p2d.quadTo(-39.960365, 49.80094, 58.815327, 33.731182);
p2d.quadTo(-30.7607, 76.5013, 123.609, 140.78426);
p2d.quadTo(-85.50613, -84.816864, -96.68608, -73.03612);
p2d.closePath();
p2d.moveTo(-94.13188, 192.13202);
p2d.quadTo(189.80658, 110.6088, 2.818996, 186.69853);
p2d.quadTo(192.63135, 33.426117, 81.44253, 125.4388);
p2d.quadTo(-45.18242, 194.22488, -34.159706, -44.944065);
p2d.quadTo(73.012436, 84.398285, 119.40327, 195.09143);
p2d.closePath();
            /*
            p2d.moveTo(-14.54943, 93.93557);
            p2d.lineTo(51.435753, 35.81831);
            p2d.lineTo(124.99598, 147.45825);
            p2d.lineTo(-36.886967, 143.34232);
            p2d.lineTo(-7.2472034, -16.372465);
            p2d.closePath();
             */
            // --- end of pasted code ---
            return p2d;
        }
    }

    static final class PanelRects extends JPanel {

        final static int max = 250 - 1;
        final static int decalX = 200;
        final static int decalY = 200;

        private Path2D.Double rect1 = createRectOutsideBounds(400, 300);
        private Path2D.Double rect2 = createRectOverBounds();

        PanelRects() {
            setPreferredSize(new Dimension(max, max));
            setBorder(new LineBorder(Color.GREEN));
        }

        @Override
        protected void paintComponent(Graphics g) {
            System.out.println("paintComponent() ---");

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

    static final class PanelRect2 extends JPanel {

        final static int max = 200 - 1;
        final static int decalX = -100;
        final static int decalY = -100;

        PanelRect2() {
            setPreferredSize(new Dimension(max, max));
            setBorder(new LineBorder(Color.GREEN));
        }

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

            Path2D.Double shape = new Path2D.Double();
            shape.moveTo(0 + decalX, 300 + decalY);
            shape.lineTo(0 + decalX, 0 + decalY);
            shape.lineTo(300 + decalX, 0 + decalY);
            shape.lineTo(300 + decalX, 300 + decalY);
            shape.lineTo(0 + decalX, 300 + decalY);

            g2d.setColor(Color.red);
            g2d.fill(shape);

            g.drawImage(bi, 0, 0, this);

            g2d.dispose();
        }
    }
}
