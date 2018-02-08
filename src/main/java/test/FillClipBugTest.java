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
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class FillClipBugTest {

    private static final int PASS = 1;

    private static boolean SAVE = true;

    public static void main(String[] args) {

        // First display which renderer is tested:
        // JDK9 only:
        System.setProperty("sun.java2d.renderer.verbose", "true");
        System.out.println("Testing renderer: ");
        // Other JDK:
        String renderer = "undefined";
        try {
            renderer = sun.java2d.pipe.RenderingEngine.getInstance().getClass().getSimpleName();
            System.out.println(renderer);
        } catch (Throwable th) {
            // may fail with JDK9 jigsaw (jake)
            if (false) {
                System.err.println("Unable to get RenderingEngine.getInstance()");
                th.printStackTrace();
            }
        }

        if (true) {
            createFrame("Test", new PanelPath(false, renderer));
//            createFrame("Test Clip", new PanelPath(true));
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

        private String rdr;

        PanelPath(final boolean showClip, final String rdr) {
            this.showClip = showClip;
            this.rdr = rdr;
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

            try {
                if (SAVE) {
                    SAVE = false; // only once
                    final File file = new File("FillClipBugTest-"+rdr+".png");

                    System.out.println("Writing file: " + file.getAbsolutePath());
                    ImageIO.write(bi, "PNG", file);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                g2d.dispose();
            }
        }

        private void setAttributes(final Graphics2D g2d) {
            g2d.setColor(Color.GRAY);

// TestSetup{id=91, shapeMode=TWO_CUBICS, closed=false, strokeWidth=10.0, strokeCap=CAP_BUTT, strokeJoin=JOIN_ROUND, dashes: [13.0, 7.0]}

            g2d.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f,
//                    new float[]{1f, 2f}, 0.0f));
                    new float[]{13f, 7f}, 0.0f));
        }

        private static Path2D.Double createPath() {
            final Path2D.Double p2d = new Path2D.Double();
            // ClipShapeTests dumped shape code:
            // --- begin of pasted code ---
if (false) {
p2d.moveTo(136.64645, 1.4918735);
p2d.curveTo(-25.203772, 126.223206, -20.010153, 131.30772, 117.95181, -27.589094);
} else {
p2d.moveTo(74.466354, 49.791237);
p2d.curveTo(66.91898, 55.68379, 60.10631, 61.002728, 54.017857, 65.7345);
}
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
