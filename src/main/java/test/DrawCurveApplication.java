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
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;

/**
 * Basic curve drawing application
 * @author bourgesl
 */
public final class DrawCurveApplication extends JPanel {

    private static final long serialVersionUID = 1L;

    static final boolean SHOW_EXTRA = true;

    static final boolean USE_DASHES = false;
    static final float STROKE_SIZE = 400.0f;
    static final float[] DASHES = (USE_DASHES)
            ? new float[]{63f, 27f}
//            ? new float[]{63000f, 27f}
            : null;

    static final boolean SHOW_QUAD = false;
    static final boolean SHOW_CUBIC = true;
    static final boolean SHOW_ELLIPSE = false;

    static final boolean SHOW_OUTLINE = SHOW_EXTRA && false;

    static final boolean PAINT_CONTROLS = SHOW_EXTRA && true;
    static final boolean PAINT_DETAILS = SHOW_EXTRA && true;
    static final boolean PAINT_TANGENT = SHOW_EXTRA && false;

    /**
     * Main
     * @param unused
     */
    public static void main(String[] unused) {
        final JFrame frame = new JFrame("Draw Curve");
        frame.getContentPane().add(new DrawCurveApplication());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(2000, 2000);
        frame.setVisible(true);
    }

    // members
    private final CanvasPanel canvas;
    private final MarkerMouseHandler handler;

    private final QuadCurve2D.Double quadCurve = new QuadCurve2D.Double();
    private final CubicCurve2D.Double cubicCurve = new CubicCurve2D.Double();

    // Ellipse
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double(
            2500, 1500, 500, 200
    );

    // Quadratic curve:
    private final Marker quadStart = new Marker(
            50 * 5, 75 * 5
    );

    private final Marker quadEnd = new Marker(
            150 * 5, 75 * 5
    );

    private final Marker quadCtrl = new Marker(
            // 80 * 5, 25 * 5
            3839.0, 928.0
    );

    // Cubic curve:
    private final Marker cubicStart = new Marker(
            50 * 5, 150 * 5
    );

    private final Marker cubicEnd = new Marker(
            150 * 5, 200 * 5
    );

    private final Marker cubicCtrl1 = new Marker(
            // 80 * 5, 100 * 5
            // 1229.0, 714.0
            // 73.0, 430.0
            // 375.0, 655.0
            214.0, 732.0
    );

    private final Marker cubicCtrl2 = new Marker(
            // 160 * 5, 100 * 5    
            // 801.0, 761.0 
            // 866.0, 830.0
            // 2108.0, 1693.0
            // 3484.0, 723.0
            // 3335.0, 439.0
            // 748.0, 1713.0
            // 1010.0, 486.0
            1532.0, 1389.0
    );

    DrawCurveApplication() {
        super(new BorderLayout());
        canvas = new CanvasPanel();
        add(canvas, BorderLayout.CENTER);

        // Marker Handler:
        this.handler = new MarkerMouseHandler();
        canvas.addMouseListener(handler);
        canvas.addMouseMotionListener(handler);

        // Quad
        if (SHOW_QUAD) {
            handler.register(quadStart.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    quadCurve.x1 = pt.x;
                    quadCurve.y1 = pt.y;
                }
            }));
            handler.register(quadCtrl.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    quadCurve.ctrlx = pt.x;
                    quadCurve.ctrly = pt.y;
                }
            }));
            handler.register(quadEnd.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    quadCurve.x2 = pt.x;
                    quadCurve.y2 = pt.y;
                }
            }));
        }

        // Cubic
        if (SHOW_CUBIC) {
            handler.register(cubicStart.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    cubicCurve.x1 = pt.x;
                    cubicCurve.y1 = pt.y;
                }
            }));
            handler.register(cubicCtrl1.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    cubicCurve.ctrlx1 = pt.x;
                    cubicCurve.ctrly1 = pt.y;
                }
            }));
            handler.register(cubicCtrl2.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    cubicCurve.ctrlx2 = pt.x;
                    cubicCurve.ctrly2 = pt.y;
                }
            }));
            handler.register(cubicEnd.setUpdater(new MarkerUpdater() {
                @Override
                public void update(final Point2D.Double pt) {
                    cubicCurve.x2 = pt.x;
                    cubicCurve.y2 = pt.y;
                }
            }));
        }
        dumpInfo();
    }

    private final class CanvasPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final BasicStroke stroke;
        private final BasicStroke strokeInfo;
        private final Color colorInfo = Color.GREEN.darker();

        private Shape ellipseOutline = null;
        private Shape quadOutline = null;
        private Shape cubicOutline = null;

        CanvasPanel() {
            stroke = new BasicStroke(STROKE_SIZE,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1000f, DASHES, 0f
            );
            strokeInfo = new BasicStroke(3f);
        }

        void reset() {
            ellipseOutline = null;
            quadOutline = null;
            cubicOutline = null;
        }

        void refresh() {
            reset();
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            final Graphics2D g2d = (Graphics2D) g;

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, getWidth(), getHeight());

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            final Stroke oldStroke = g2d.getStroke();
            g2d.setStroke(stroke);
            g2d.setPaint(Color.LIGHT_GRAY);

            System.out.println("g2D.draw() before");

            if (SHOW_QUAD) {
                g2d.draw(quadCurve);
            }
            if (SHOW_CUBIC) {
                g2d.draw(cubicCurve);
            }
            if (SHOW_ELLIPSE) {
                g2d.draw(ellipse);
            }

            System.out.println("g2D.draw() after");

            if (PAINT_CONTROLS) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // xrender calls Marlin anyway
                g2d.setStroke(strokeInfo);
                g2d.setPaint(colorInfo);

                for (Marker marker : handler.getMarkers()) {
                    marker.draw(g2d);
                }
                // draw tangents:
                if (SHOW_QUAD) {
                    LINE.setLine(quadStart.getPoint(), quadCtrl.getPoint());
                    g2d.draw(LINE);
                    LINE.setLine(quadEnd.getPoint(), quadCtrl.getPoint());
                    g2d.draw(LINE);
                }
                if (SHOW_CUBIC) {
                    LINE.setLine(cubicStart.getPoint(), cubicCtrl1.getPoint());
                    g2d.draw(LINE);
                    LINE.setLine(cubicEnd.getPoint(), cubicCtrl2.getPoint());
                    g2d.draw(LINE);
                }

                // draw curves:
                if (SHOW_QUAD) {
                    g2d.draw(quadCurve);
                }
                if (SHOW_CUBIC) {
                    g2d.draw(cubicCurve);
                }
                if (SHOW_ELLIPSE) {
                    g2d.draw(ellipse);
                }
            }
            // draw outlines:
            if (PAINT_DETAILS) {
                if (SHOW_ELLIPSE && ellipseOutline == null) {
                    ellipseOutline = stroke.createStrokedShape(ellipse);
                }
                if (SHOW_QUAD && quadOutline == null) {
                    quadOutline = stroke.createStrokedShape(quadCurve);
                }
                if (SHOW_CUBIC && cubicOutline == null) {
                    cubicOutline = stroke.createStrokedShape(cubicCurve);
                }
                if (SHOW_OUTLINE) {
                    g2d.setPaint(Color.DARK_GRAY);
                    if (SHOW_QUAD) {
                        g2d.draw(quadOutline);
                    }
                    if (SHOW_CUBIC) {
                        g2d.draw(cubicOutline);
                    }
                    if (SHOW_ELLIPSE) {
                        g2d.draw(ellipseOutline);
                    }
                }
                if (SHOW_QUAD) {
                    paintShapeDetails(g2d, quadOutline);
                }
                if (SHOW_CUBIC) {
                    paintShapeDetails(g2d, cubicOutline);
                }
                if (SHOW_ELLIPSE) {
                    paintShapeDetails(g2d, ellipseOutline);
                }
            }
            g2d.setStroke(oldStroke);
        }
    }

    interface MarkerUpdater {

        public void update(final Point2D.Double pt);
    }

    final static class Marker {

        private static final double POINT_DIAM = 30.0;
        private static final double POINT_OFF = POINT_DIAM / 2.0 - 0.5;

        private final Point2D.Double point;
        private MarkerUpdater updater = null;
        private final Ellipse2D.Double ellipse;

        Marker(final double x, final double y) {
            this.point = new Point2D.Double();
            this.ellipse = new Ellipse2D.Double();
            setLocation(x, y);
        }

        public Point2D.Double getPoint() {
            return point;
        }

        public MarkerUpdater getUpdater() {
            return updater;
        }

        public Marker setUpdater(final MarkerUpdater updater) {
            this.updater = updater;
            update();
            return this;
        }

        public void update() {
            if (updater != null) {
                updater.update(point);
            }
        }

        public void draw(Graphics2D g2D) {
            g2D.draw(ellipse);
        }

        public boolean contains(final double x, final double y) {
            return ellipse.contains(x, y);
        }

        public void setLocation(final double x, final double y) {
            point.setLocation(x, y);
            ellipse.setFrame(x - POINT_OFF, y - POINT_OFF, POINT_DIAM, POINT_DIAM);
            update();
        }
    }

    private final class MarkerMouseHandler extends MouseInputAdapter {

        private final ArrayList<Marker> markers;
        private Marker selected = null;

        MarkerMouseHandler() {
            markers = new ArrayList<Marker>();
        }

        void register(final Marker marker) {
            markers.add(marker);
        }

        void unregister(final Marker marker) {
            markers.remove(marker);
            selected = null;
        }

        public ArrayList<Marker> getMarkers() {
            return markers;
        }

        @Override
        public void mousePressed(final MouseEvent me) {
            selected = null;

            for (Marker marker : markers) {
                if (marker.contains(me.getX(), me.getY())) {
                    selected = marker;
                }
            }
        }

        @Override
        public void mouseReleased(final MouseEvent me) {
            dumpInfo();
            selected = null;
        }

        @Override
        public void mouseDragged(final MouseEvent me) {
            if (selected != null) {
                selected.setLocation(me.getX(), me.getY());
                canvas.refresh();
            }
        }
    }

    void dumpInfo() {
        if (SHOW_QUAD) {
            dumpShape(quadCurve);
        }
        if (SHOW_CUBIC) {
            dumpShape(cubicCurve);
        }
    }

    // --- dump shape ---
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

    // --- paint details ---
    static final double POINT_DIAM = 10.0;
    static final double POINT_OFF = POINT_DIAM / 2.0 - 0.5;

    static final Stroke STROKE_ODD = new BasicStroke(1.0f);
    static final Stroke STROKE_EVEN = new BasicStroke(1.0f);
    static final int COLOR_ALPHA = 220;
    static final Color COLOR_MOVETO = new Color(0, 255, 0, COLOR_ALPHA);
    static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, COLOR_ALPHA);
    static final Color COLOR_LINETO_EVEN = new Color(255, 0, 0, COLOR_ALPHA);

    private final QuadCurve2D.Double QUAD = new QuadCurve2D.Double();
    private final CubicCurve2D.Double CUBIC = new CubicCurve2D.Double();

    private final Line2D.Double LINE = new Line2D.Double();
    private final Ellipse2D.Float ELL_POINT = new Ellipse2D.Float();

    private final double[] coords = new double[6];

    private void paintShapeDetails(final Graphics2D g2d, final Shape shape) {
        final Stroke oldStroke = g2d.getStroke();
        final Color oldColor = g2d.getColor();

        int nOp = 0;

        double sx0 = 0, sy0 = 0, x0 = 0, y0 = 0;

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    g2d.setColor(COLOR_MOVETO);
                    g2d.setStroke(STROKE_ODD);
                    ELL_POINT.setFrame(coords[0] - POINT_OFF, coords[1] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                    g2d.fill(ELL_POINT);
                    x0 = coords[0];
                    y0 = coords[1];
                    sx0 = x0;
                    sy0 = y0;
                    break;
                case PathIterator.SEG_LINETO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    ELL_POINT.setFrame(coords[0] - POINT_OFF, coords[1] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                    g2d.fill(ELL_POINT);

                    g2d.setStroke((nOp % 2 == 0) ? STROKE_ODD : STROKE_EVEN);
                    LINE.setLine(x0, y0, coords[0], coords[1]);
                    g2d.draw(LINE);
                    x0 = coords[0];
                    y0 = coords[1];
                    break;
                case PathIterator.SEG_QUADTO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    if (PAINT_TANGENT) {
                        ELL_POINT.setFrame(coords[0] - POINT_OFF, coords[1] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                        g2d.fill(ELL_POINT);
                    }
                    ELL_POINT.setFrame(coords[2] - POINT_OFF, coords[3] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                    g2d.fill(ELL_POINT);

                    g2d.setStroke((nOp % 2 == 0) ? STROKE_ODD : STROKE_EVEN);
                    QUAD.setCurve(x0, y0, coords[0], coords[1], coords[2], coords[3]);
                    g2d.draw(QUAD);

                    if (PAINT_TANGENT) {
                        LINE.setLine(x0, y0, coords[0], coords[1]);
                        g2d.draw(LINE);
                        LINE.setLine(coords[0], coords[1], coords[2], coords[3]);
                        g2d.draw(LINE);
                    }
                    x0 = coords[2];
                    y0 = coords[3];
                    nOp++;
                    break;
                case PathIterator.SEG_CUBICTO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    if (PAINT_TANGENT) {
                        ELL_POINT.setFrame(coords[0] - POINT_OFF, coords[1] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                        g2d.fill(ELL_POINT);
                        ELL_POINT.setFrame(coords[2] - POINT_OFF, coords[3] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                        g2d.fill(ELL_POINT);
                    }
                    ELL_POINT.setFrame(coords[4] - POINT_OFF, coords[5] - POINT_OFF, POINT_DIAM, POINT_DIAM);
                    g2d.fill(ELL_POINT);

                    g2d.setStroke((nOp % 2 == 0) ? STROKE_ODD : STROKE_EVEN);
                    CUBIC.setCurve(x0, y0, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    g2d.draw(CUBIC);

                    if (PAINT_TANGENT) {
                        LINE.setLine(x0, y0, coords[0], coords[1]);
                        g2d.draw(LINE);
                        LINE.setLine(coords[2], coords[3], coords[4], coords[5]);
                        g2d.draw(LINE);
                    }
                    x0 = coords[4];
                    y0 = coords[5];
                    nOp++;
                    break;
                case PathIterator.SEG_CLOSE:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    g2d.setStroke((nOp % 2 == 0) ? STROKE_ODD : STROKE_EVEN);
                    LINE.setLine(x0, y0, sx0, sy0);
                    g2d.draw(LINE);
                    x0 = sx0;
                    y0 = sy0;
                    continue;
                default:
                    System.out.println("unsupported segment type= " + type);
            }
        }
        g2d.setStroke(oldStroke);
        g2d.setColor(oldColor);
    }
}
