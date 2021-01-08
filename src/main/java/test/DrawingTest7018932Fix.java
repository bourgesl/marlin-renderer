package test;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;

import javax.swing.JFrame;
import javax.swing.JPanel;
import org.marlin.pisces.DMarlinRenderingEngine;
import org.marlin.pisces.DPathConsumer2D;
import org.marlin.pisces.MarlinUtils;
import sun.awt.SunHints;
import sun.awt.geom.PathConsumer2D;
import sun.java2d.SunGraphics2D;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.RenderingEngine;
import sun.java2d.pipe.ShapeSpanIterator;

public class DrawingTest7018932Fix extends JPanel {

    static final boolean useAA = true;

    private static final RenderingEngine renderEngine = RenderingEngine.getInstance();

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(new DrawingTest7018932Fix(), BorderLayout.CENTER);
        frame.setSize(400, 400);
        frame.setVisible(true);
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        if (useAA) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

// clip - doesn't help
        g2.setClip(0, 0, getWidth(), getHeight());

// this part is just testing the drawing - so I can see I am actually drawing something
// IGNORE
        /**
     g.setColor(Color.GREEN);
     g.fillRect(0, 0, getWidth(), getHeight());
     g.setColor(Color.black);
        g2.setStroke(new BasicStroke(2));
     g2.draw(new Line2D.Double(20, 20, 200, 20));
    
     /**/
// Now we re-create the exact conditions that lead to the system crash in the JDK
// BUG HERE - setting the stroke leads to the crash
        Stroke stroke = new BasicStroke(2.0f, 1, 0, 1.0f, new float[]{0.0f, 4.0f}, 0.0f);
        g2.setStroke(stroke);

        // NOTE: Large values to trigger crash / infinite loop?
        final Shape s = new Line2D.Double(4.0, 1.794369841E9, 567.0, -2.147483648E9);

        if (true && !useAA) {
            // LBO: equivalent via LoopPipe.getStrokeSpans() to calling:
            try {
                final SunGraphics2D sg2d = (SunGraphics2D) g2;
                final Region clip = sg2d.getCompClip();

                BasicStroke bs = (BasicStroke) g2.getStroke();
                boolean thin = (sg2d.strokeState <= SunGraphics2D.STROKE_THINDASHED);
                boolean normalize = (sg2d.strokeHint != SunHints.INTVAL_STROKE_PURE);

                if (renderEngine instanceof DMarlinRenderingEngine) {
                    // for jdk 17+:
                    final DMarlinRenderingEngine marlinRenderEngine = (DMarlinRenderingEngine) renderEngine;
                    marlinRenderEngine.strokeTo(s,
                            sg2d.transform, clip /* ADDED */, bs,
                            thin, normalize, false, new PathTracer("TEST"));

                } else {
                    // before jdk 17:
                    renderEngine.strokeTo(s,
                            sg2d.transform, bs,
                            thin, normalize, false, new PathTracer("TEST"));
                }
            } catch (Throwable t) {
                throw new InternalError("Unable to Stroke shape ("
                        + t.getMessage() + ")", t);
            }
        } else {
            g2.draw(s);
        }
    }

    static final class PathTracer implements PathConsumer2D {

        private final String prefix;
        private int count;

        PathTracer(String name) {
            this.prefix = name + ": ";
            count = 0;
        }

        @Override
        public void moveTo(float x0, float y0) {
            log("p.moveTo(" + x0 + ", " + y0 + ");");
            count++;
        }

        @Override
        public void lineTo(float x1, float y1) {
            log("p.lineTo(" + x1 + ", " + y1 + ");");
            count++;
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3) {
            log("p.curveTo(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ", " + x3 + ", " + y3 + ");");
            count++;
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2) {
            log("p.quadTo(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ");");
            count++;
        }

        @Override
        public void closePath() {
            log("p.closePath();");
        }

        @Override
        public void pathDone() {
            log("p.pathDone(): count = " + count);
        }

        private void log(final String message) {
            System.out.println(prefix + message);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }
}
