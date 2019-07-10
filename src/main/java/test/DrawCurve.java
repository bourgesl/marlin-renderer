package test;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;

/**
 *
 */
public class DrawCurve {

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.getContentPane().add(new CurveApplet());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 1000);
        frame.setVisible(true);
    }
}

class CurveApplet extends JPanel {

    public CurveApplet() {
        super(new BorderLayout());
        pane = new CurvePane();
        add(pane, "Center");

        MouseHandler handler = new MouseHandler();
        pane.addMouseListener(handler);
        pane.addMouseMotionListener(handler);
    }

    class CurvePane extends JComponent {

        private final BasicStroke stroke;

        public CurvePane() {
            quadCurve = new QuadCurve2D.Double(
                    startQ.x, startQ.y,
                    control.x, control.y,
                    endQ.x, endQ.y);

            cubicCurve = new CubicCurve2D.Double(
                    startC.x, startC.y,
                    controlStart.x, controlStart.y,
                    controlEnd.x, controlEnd.y,
                    endC.x, endC.y);

            stroke = new BasicStroke(80f,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 100f
                    , new float[]{63f, 27f}, 0f
            );
        }

        public void paint(Graphics g) {
            Graphics2D g2D = (Graphics2D) g;

            g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2D.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            quadCurve.ctrlx = ctrlQuad.getCenter().x;
            quadCurve.ctrly = ctrlQuad.getCenter().y;
            cubicCurve.ctrlx1 = ctrlCubic1.getCenter().x;
            cubicCurve.ctrly1 = ctrlCubic1.getCenter().y;
            cubicCurve.ctrlx2 = ctrlCubic2.getCenter().x;
            cubicCurve.ctrly2 = ctrlCubic2.getCenter().y;

            Stroke oldStroke = g2D.getStroke();
            g2D.setStroke(stroke);
            g2D.setPaint(Color.BLUE);
            g2D.draw(quadCurve);
            g2D.draw(cubicCurve);
            g2D.setStroke(oldStroke);

            g2D.setPaint(Color.RED);
            ctrlQuad.draw(g2D);
            ctrlCubic1.draw(g2D);
            ctrlCubic2.draw(g2D);

            Line2D.Double tangent = new Line2D.Double(startQ, ctrlQuad.getCenter());
            g2D.draw(tangent);
            tangent = new Line2D.Double(endQ, ctrlQuad.getCenter());
            g2D.draw(tangent);

            tangent = new Line2D.Double(startC, ctrlCubic1.getCenter());
            g2D.draw(tangent);
            tangent = new Line2D.Double(endC, ctrlCubic2.getCenter());
            g2D.draw(tangent);
        }
    }

    Point2D.Double startQ = new Point2D.Double(50 *5, 75 *5);

    Point2D.Double endQ = new Point2D.Double(150 *5, 75 *5);

    Point2D.Double control = new Point2D.Double(80 *5, 25 *5);

    Point2D.Double startC = new Point2D.Double(50 *5, 150 *5);

    Point2D.Double endC = new Point2D.Double(150 *5, 150 *5);

    Point2D.Double controlStart = new Point2D.Double(80 *5, 100 *5);

    Point2D.Double controlEnd = new Point2D.Double(160 *5, 100 *5);

    Marker ctrlQuad = new Marker(control);

    Marker ctrlCubic1 = new Marker(controlStart);

    Marker ctrlCubic2 = new Marker(controlEnd);

    QuadCurve2D.Double quadCurve;

    CubicCurve2D.Double cubicCurve;

    CurvePane pane = new CurvePane();

    class Marker {

        public Marker(Point2D.Double control) {
            center = control;
            circle = new Ellipse2D.Double(control.x - radius, control.y - radius, 2.0 * radius,
                    2.0 * radius);
        }

        public void draw(Graphics2D g2D) {
            g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2D.draw(circle);
        }

        Point2D.Double getCenter() {
            return center;
        }

        public boolean contains(double x, double y) {
            return circle.contains(x, y);
        }

        public void setLocation(double x, double y) {
            center.x = x;
            center.y = y;
            circle.x = x - radius;
            circle.y = y - radius;
        }

        Ellipse2D.Double circle;

        Point2D.Double center;

        static final double radius = 10;
    }

    class MouseHandler extends MouseInputAdapter {

        public void mousePressed(MouseEvent e) {
            if (ctrlQuad.contains(e.getX(), e.getY())) {
                selected = ctrlQuad;
            } else if (ctrlCubic1.contains(e.getX(), e.getY())) {
                selected = ctrlCubic1;
            } else if (ctrlCubic2.contains(e.getX(), e.getY())) {
                selected = ctrlCubic2;
            }
        }

        public void mouseReleased(MouseEvent e) {

            dumpShape(quadCurve);
            dumpShape(cubicCurve);

            selected = null;
        }

        public void mouseDragged(MouseEvent e) {
            if (selected != null) {
                selected.setLocation(e.getX(), e.getY());
                pane.repaint();
            }
        }
        Marker selected = null;
    }


    private static void dumpShape(final Shape shape) {
        final float[] coords = new float[6];

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            final int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    System.out.println("p2d.moveTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_LINETO:
                    System.out.println("p2d.lineTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_QUADTO:
                    System.out.println("p2d.quadTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ");");
                    break;
                case PathIterator.SEG_CUBICTO:
                    System.out.println("p2d.curveTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ", " + coords[4] + ", " + coords[5] + ");");
                    break;
                case PathIterator.SEG_CLOSE:
                    System.out.println("p2d.closePath();");
                    break;
                default:
                    System.out.println("// Unsupported segment type= " + type);
            }
        }
        System.out.println("--------------------------------------------------");
    }

}
