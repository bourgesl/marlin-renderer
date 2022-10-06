/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package test;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.MouseInputAdapter;
import org.marlin.pisces.MarlinDebugThreadLocal;

/**
 * Basic curve drawing application

Loop case:
p2d.moveTo(1690.0, 191.0);
p2d.curveTo(1650.0, 557.0, 503.0, 680.0, 915.0, 694.0);

Bug cubic:
--------------------------------------------------
p2d.moveTo(250.0, 750.0);
p2d.curveTo(3024.0, 792.0, 2470.0, 774.0, 3374.0, 1758.0);

 */
public final class DrawCurveApplication extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final boolean USE_SPECIFIC_TEST = true;
    private static final int TEST_CASE = 4;

    private static final boolean TEST_EAR_LOOP = (TEST_CASE == 1);
    /** default timeline refresh period = 200ms ie 5 fps */
    private static final int REFRESH_PERIOD = 20;

    protected static int PAINT_COUNT = 0;

    /**
     * Main
     * @param unused
     */
    public static void main(String[] unused) {
        // Set the default locale to en-US locale (for Numerical Fields "." ",")
        Locale.setDefault(Locale.US);

        // disable static curve subdivision setting:
        System.setProperty("sun.java2d.renderer.betterCurves", "false");
        System.setProperty("sun.java2d.renderer.betterCurves.runtime.enable", "true");

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
        final boolean isMarlin = renderer.contains("MarlinRenderingEngine");

        SwingUtilities.invokeLater(new Runnable() {

            /**
             * Create the Gui using EDT
             */
            @Override
            public void run() {
                final DrawCurveApplication appPanel = new DrawCurveApplication(isMarlin);
                final DrawCurveSettingsPanel paramsPanel = new DrawCurveSettingsPanel();
                paramsPanel.setApp(appPanel);

                final JFrame frame = new JFrame("Canvas");
                frame.getContentPane().add(appPanel);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setSize(2000, 2000);
                frame.setVisible(true);

                final JFrame frameParams = new JFrame("Parameters");
                frameParams.getContentPane().add(paramsPanel);
                frameParams.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frameParams.pack();
                frameParams.setLocation(2050, 150);
                frameParams.setVisible(true);
                frameParams.toFront();
            }
        });
    }

    // settings
    // Marlin parameters:
    final boolean isMarlin;
    AtomicBoolean betterCurves = new AtomicBoolean(false);

    // stroke parameters:
    int alphaPaint = 200;
    float strokeWidth = 400.0f;
    int strokeCap = BasicStroke.CAP_BUTT;
    int strokeJoin = BasicStroke.JOIN_BEVEL;

    boolean useDashes = false;
    float[] dashes
            = new float[]{63f, 27f};
//            = new float[]{63000f, 27f};

    // shape parameters:
    AtomicBoolean showQuad = new AtomicBoolean(false);
    AtomicBoolean showCubic = new AtomicBoolean(true);
    boolean showEllipse = false;

    // painting parameters:
    boolean showExtra = true;
    boolean showOutline = false;
    boolean paintControls = true;
    boolean paintDetails = true;
    boolean paintDetailsTangent = true;
    boolean paintMidpoint = false;

    /** refresh Swing timer */
    private final Timer timerTimeRefresh;

    // members
    private final CanvasPanel canvas;
    private final MarkerMouseHandler handler;

    private final QuadCurve2D.Double quadCurve = new QuadCurve2D.Double();
    private final CubicCurve2D.Double cubicCurve = new CubicCurve2D.Double();

    // Ellipse
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double(
            2500, 1200, 500, 400
    );

    // Quadratic curve:
    private final Marker quadStart = new Marker(1, showQuad,
            50 * 5, 75 * 5
    );

    private final Marker quadCtrl = new Marker(2, showQuad,
            // 80 * 5, 25 * 5
            3839.0, 928.0
    );

    private final Marker quadEnd = new Marker(3, showQuad,
            150 * 5, 75 * 5
    );

    // Cubic curve:
    private final Marker cubicStart = new Marker(1, showCubic,
            50 * 5, 150 * 5
    );

    private final Marker cubicCtrl1 = new Marker(2, showCubic,
            // 80 * 5, 100 * 5
            // 1229.0, 714.0
            // 73.0, 430.0
            // 375.0, 655.0
            //            214.0, 732.0
            783.0, 859.0
    );

    private final Marker cubicCtrl2 = new Marker(3, showCubic,
            // 160 * 5, 100 * 5
            // 801.0, 761.0
            // 866.0, 830.0
            // 2108.0, 1693.0
            // 3484.0, 723.0
            // 3335.0, 439.0
            // 748.0, 1713.0
            // 1010.0, 486.0
            //            1532.0, 1389.0
            565.0, 1752.0
    );

    private final Marker cubicEnd = new Marker(4, showCubic,
            150 * 5, 200 * 5
    );

    // TODO: test bad case:
    /*
p2d.moveTo(250.0, 750.0);
p2d.curveTo(816.0, 1949.0, 1199.0, 1666.0, 2029.0, 1359.0);
     */
 /*
    Loop sample with artefact:
p2d.moveTo(2995.0, 442.0);
p2d.curveTo(354.0, 1849.0, 1723.0, 132.0, 1269.0, 2026.0);
     */
    DrawCurveApplication(final boolean isMarlin) {
        super(new BorderLayout());
        this.isMarlin = isMarlin;

        canvas = new CanvasPanel();
        add(canvas, BorderLayout.CENTER);

        // Marker Handler:
        this.handler = new MarkerMouseHandler(this);
        canvas.addMouseListener(handler);
        canvas.addMouseMotionListener(handler);

        if (USE_SPECIFIC_TEST) {
            switch (TEST_CASE) {
                case 0:
                    moveTo(997.4651258477551, 1122.8952188708217);
                    curveTo(998.0416661761394, 1123.1895282578275,
                            998.3357637878879, 1123.3431861242257,
                            998.3357637878879, 1123.3503852683693);
                    break;
                case 1: // EAR LOOP
                    moveTo(250.0, 750.0);
                    curveTo(2500.0, 838.0, 2388.0, 799.0, 3374.0, 1758.0);
                    break;
                case 2:
                    moveTo(9.0, 507.0);
                    curveTo(800.0, 2017.0, 56.0, 212.0, 3058.0, -19.0);
                    break;
                case 3:
                    // TODO: in progress 1 bad point:
                    moveTo(208.0, 313.0);
                    curveTo(639.0, 1088.0, 1339.0, 1671.0, 1449.0, 475.0);
                    /*
                    moveTo(250.0, 750.0);
                    curveTo(783.0, 859.0, 1339.0, 1671.0, 1449.0, 475.0);
                     */
                    break;
                case 4:
                    moveTo(208.0, 313.0);
                    curveTo(3646.0, 758.0, 2720.0, -12.0, 109.0, 1199.0);
                    break;
                case 5:
                    //inflexion bug:                    
                    moveTo(387.0, 1238.0);
                    curveTo(2815.0, 1824.0, 505.0, 1720.0, 2231.0, 329.0);
                    break;
            }

            // Create the timeline refresh timer:
            this.timerTimeRefresh = new Timer(REFRESH_PERIOD, new ActionListener() {
                /**
                 * Invoked when the timer action occurs.
                 */
                @Override
                public void actionPerformed(final ActionEvent ae) {
                    refresh();
                }
            });

        }

        // Quad
        final QuadMarkerUpdater quadUpdater = new QuadMarkerUpdater(quadCurve);
        handler.register(quadStart.setUpdater(quadUpdater));
        handler.register(quadCtrl.setUpdater(quadUpdater));
        handler.register(quadEnd.setUpdater(quadUpdater));

        // Cubic
        final CubicMarkerUpdater cubicUpdater = new CubicMarkerUpdater(cubicCurve);
        handler.register(cubicStart.setUpdater(cubicUpdater));
        handler.register(cubicCtrl1.setUpdater(cubicUpdater));
        handler.register(cubicCtrl2.setUpdater(cubicUpdater));
        handler.register(cubicEnd.setUpdater(cubicUpdater));

        dumpInfo();
    }

    // mimic path2d commands:
    void moveTo(double x0, double y0) {
        cubicStart.setLocation(x0, y0);
    }

    void curveTo(final double x1, final double y1,
                 final double x2, final double y2,
                 final double x3, final double y3) {

        cubicCtrl1.setLocation(x1, y1);
        cubicCtrl2.setLocation(x2, y2);
        cubicEnd.setLocation(x3, y3);
    }

    void cubicTo(double x0, double y0,
                 final double x1, final double y1,
                 final double x2, final double y2,
                 final double x3, final double y3) {
        moveTo(x0, y0);
        curveTo(x1, y1, x2, y2, x3, y3);
    }

    void dumpInfo() {
        if (showQuad.get()) {
            dumpShape(quadCurve);
        }
        if (showCubic.get()) {
            dumpShape(cubicCurve);
        }
    }

    void refresh() {
        canvas.refresh();
    }

    /**
     * Return true if the timer is enabled
     * @return true if the timer is enabled
     */
    public boolean isTimerEnabled() {
        return this.timerTimeRefresh.isRunning();
    }

    /**
     * Start/Stop the internal Refresh timer
     * @param enable true to enable it, false otherwise
     */
    public void enableTimerRefreshTimer(final boolean enable) {
        if (enable) {
            if (!this.timerTimeRefresh.isRunning()) {
                // System.out.println("Starting timer: " + this.timerTimeRefresh);
                this.timerTimeRefresh.start();
            }
        } else {
            if (this.timerTimeRefresh.isRunning()) {
                // System.out.println("Stopping timer: " + this.timerTimeRefresh);
                this.timerTimeRefresh.stop();
            }
        }
    }

    private final class CanvasPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private BasicStroke stroke = null;
        private final BasicStroke strokeInfo = new BasicStroke(3f);
        private final Color colorInfo = Color.GREEN.darker();

        private Shape ellipseOutline = null;
        private Shape quadOutline = null;
        private Shape cubicOutline = null;

        CanvasPanel() {
            super();
            reset();
        }

        private void reset() {
            stroke = null;
            ellipseOutline = null;
            quadOutline = null;
            cubicOutline = null;
            if (showExtra && paintDetails) {
                computeBrush();
            }
        }

        float oldw = 0.0f;
        double[] xc = null;
        double[] yc = null;

        private final double MIN_ANG = Math.toRadians(0.0);

        private void computeBrush() {
            if ((xc == null) || (oldw != strokeWidth)) {
                oldw = strokeWidth;
                final double w = strokeWidth / 2.0;

                final double angStep = Math.max(MIN_ANG, Math.atan2(1.0, w));
//            System.out.println("angStep: " + Math.toDegrees(angStep));

                final int np = (int) Math.ceil((2.0 * Math.PI) / angStep);
                System.out.println("np: " + np);

                xc = new double[np];
                yc = new double[np];

                for (int i = 0; i < np; i++) {
                    double ang = angStep * i;
                    xc[i] = w * Math.cos(ang);
                    yc[i] = w * Math.sin(ang);
//                System.out.println("c["+i+"]: (" + xc[i] + ", "+yc[i]+")");
                }
            }
        }

        void refresh() {
            reset();
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            PAINT_COUNT++;
            final Graphics2D g2d = (Graphics2D) g;

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            final int w = getWidth();
            final int h = getHeight();

            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, w, h);

            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // draw outlines:
            if (showExtra && paintDetails) {
                // draw enveloppe using sampled brush:
                if (showQuad.get()) {
                    drawEnveloppe(g2d, quadCurve, xc, yc);
                }
                if (showCubic.get()) {
                    drawEnveloppe(g2d, cubicCurve, xc, yc);
                }
                if (showEllipse) {
                    drawEnveloppe(g2d, ellipse, xc, yc);
                }
            }

            if (isMarlin) {
                // Enable or Disable curve subdivision:
                System.setProperty("sun.java2d.renderer.betterCurves.runtime", (betterCurves.get()) ? "true" : "false");
            }

            final long start = System.nanoTime();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            final Stroke oldStroke = g2d.getStroke();
            if (stroke == null) {
                stroke = new BasicStroke(strokeWidth, strokeCap, strokeJoin, 1000f,
                        (useDashes) ? dashes : null, 0f
                );
            }
            g2d.setStroke(stroke);

            final Color paint = new Color(192, 192, 192, alphaPaint);
            g2d.setPaint(paint);

            // start recording Marlin's debug info:
            MarlinDebugThreadLocal.startRecord();
//            System.out.println("g2D.draw() before");

            if (USE_SPECIFIC_TEST && TEST_EAR_LOOP) {
                g2d.translate(-2800.0, -2700);
                g2d.scale(3.3, 3.3);
            }
            // create stroked shape ONCE per rendering operation:
            if (showQuad.get()) {
                quadOutline = stroke.createStrokedShape(quadCurve);
                g2d.fill(quadOutline);
            }
            if (showCubic.get()) {
                cubicOutline = stroke.createStrokedShape(cubicCurve);
                g2d.fill(cubicOutline);
            }
            if (showEllipse) {
                ellipseOutline = stroke.createStrokedShape(ellipse);
                g2d.fill(ellipseOutline);
            }

            // stop recording Marlin's debug info:
            MarlinDebugThreadLocal.stopRecord();
//            System.out.println("g2D.draw() after");

            final long time = System.nanoTime() - start;
            System.out.println("paint: duration= " + (time / 1000l) + " µs.");

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF); // xrender calls Marlin anyway

            if (paintControls) {
                g2d.setStroke(strokeInfo);
                g2d.setPaint(colorInfo);

                for (Marker marker : handler.getMarkers()) {
                    marker.draw(g2d);
                }
                // draw tangents:
                if (showQuad.get()) {
                    LINE.setLine(quadStart.getPoint(), quadCtrl.getPoint());
                    g2d.draw(LINE);
                    LINE.setLine(quadEnd.getPoint(), quadCtrl.getPoint());
                    g2d.draw(LINE);
                }
                if (showCubic.get()) {
                    LINE.setLine(cubicStart.getPoint(), cubicCtrl1.getPoint());
                    g2d.draw(LINE);
                    LINE.setLine(cubicEnd.getPoint(), cubicCtrl2.getPoint());
                    g2d.draw(LINE);
                }

                // draw curves:
                if (showQuad.get()) {
                    g2d.draw(quadCurve);
                }
                if (showCubic.get()) {
                    g2d.draw(cubicCurve);
                }
                if (showEllipse) {
                    g2d.draw(ellipse);
                }
            }
            // draw outlines:
            if (showExtra && paintDetails) {
                if (showExtra && showOutline) {
                    g2d.setPaint(Color.DARK_GRAY);
                    if (showQuad.get()) {
                        g2d.draw(quadOutline);
                    }
                    if (showCubic.get()) {
                        g2d.draw(cubicOutline);
                    }
                    if (showEllipse) {
                        g2d.draw(ellipseOutline);
                    }
                }
                if (showQuad.get()) {
                    paintShapeDetails(g2d, quadOutline, w, h);
                }
                if (showCubic.get()) {
                    paintShapeDetails(g2d, cubicOutline, w, h);
                }
                if (showEllipse) {
                    paintShapeDetails(g2d, ellipseOutline, w, h);
                }
            }
            g2d.setStroke(oldStroke);
        }
    }

    interface MarkerUpdater {

        public void update(final int index, final Point2D.Double pt);
    }

    final static class QuadMarkerUpdater implements MarkerUpdater {

        private final QuadCurve2D.Double quadCurve;

        QuadMarkerUpdater(final QuadCurve2D.Double quadCurve) {
            this.quadCurve = quadCurve;
        }

        @Override
        public void update(final int index, final Point2D.Double pt) {
            switch (index) {
                case 1: // P1 = start point
                    quadCurve.x1 = pt.x;
                    quadCurve.y1 = pt.y;
                    return;
                case 2: // P2 = control point
                    quadCurve.ctrlx = pt.x;
                    quadCurve.ctrly = pt.y;
                    return;
                case 3: // P3 = end point
                    quadCurve.x2 = pt.x;
                    quadCurve.y2 = pt.y;
                    return;
                default:
            }
        }
    }

    final static class CubicMarkerUpdater implements MarkerUpdater {

        private final CubicCurve2D.Double cubicCurve;

        CubicMarkerUpdater(final CubicCurve2D.Double cubicCurve) {
            this.cubicCurve = cubicCurve;
        }

        @Override
        public void update(final int index, final Point2D.Double pt) {
            switch (index) {
                case 1: // P1 = start point
                    cubicCurve.x1 = pt.x;
                    cubicCurve.y1 = pt.y;
                    return;
                case 2: // P2 = control point 1
                    cubicCurve.ctrlx1 = pt.x;
                    cubicCurve.ctrly1 = pt.y;
                    return;
                case 3: // P3 = control point 2
                    cubicCurve.ctrlx2 = pt.x;
                    cubicCurve.ctrly2 = pt.y;
                    return;
                case 4: // P4 = end point
                    cubicCurve.x2 = pt.x;
                    cubicCurve.y2 = pt.y;
                    return;
                default:
            }
        }
    }

    final static class Marker {

        private static final double POINT_DIAM = 30.0;
        private static final double POINT_OFF = POINT_DIAM / 2.0 - 0.5;

        // members:
        private final int index;
        private final Point2D.Double point;
        private final AtomicBoolean show;
        private MarkerUpdater updater = null;
        private final Ellipse2D.Double ellipse;

        Marker(final int index, final AtomicBoolean show, final double x, final double y) {
            this.index = index;
            this.point = new Point2D.Double();
            this.show = show;
            this.ellipse = new Ellipse2D.Double();
            setLocation(x, y);
        }

        public int getIndex() {
            return index;
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
                updater.update(index, point);
            }
        }

        public void draw(final Graphics2D g2D) {
            if (show.get()) {
                g2D.draw(ellipse);
            }
        }

        public boolean contains(final double x, final double y) {
            return show.get() && ellipse.contains(x, y);
        }

        public void setLocation(final double x, final double y) {
            point.setLocation(x, y);
            ellipse.setFrame(x - POINT_OFF, y - POINT_OFF, POINT_DIAM, POINT_DIAM);
            update();
        }
    }

    private static final class MarkerMouseHandler extends MouseInputAdapter {

        private final DrawCurveApplication app;
        private final ArrayList<Marker> markers;
        private Marker selected = null;

        MarkerMouseHandler(DrawCurveApplication app) {
            this.app = app;
            this.markers = new ArrayList<Marker>();
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
            app.dumpInfo();
            selected = null;
        }

        @Override
        public void mouseDragged(final MouseEvent me) {
            if (selected != null) {
                selected.setLocation(me.getX(), me.getY());
                app.refresh();
            }
        }
    }

    // --- dump shape ---
    private static void dumpShape(final Shape shape) {
        final float[] coords = new float[6];

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            final int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    System.out.println("moveTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_LINETO:
                    System.out.println("lineTo(" + coords[0] + ", " + coords[1] + ");");
                    break;
                case PathIterator.SEG_QUADTO:
                    System.out.println("quadTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ");");
                    break;
                case PathIterator.SEG_CUBICTO:
                    System.out.println("curveTo(" + coords[0] + ", " + coords[1] + ", " + coords[2] + ", " + coords[3] + ", " + coords[4] + ", " + coords[5] + ");");
                    break;
                case PathIterator.SEG_CLOSE:
                    System.out.println("closePath();");
                    break;
                default:
                    System.out.println("// Unsupported segment type= " + type);
            }
        }
        System.out.println("--------------------------------------------------");
    }

    // --- draw enveloppe ---
    private static void drawEnveloppe(final Graphics2D g2d, final Shape shape,
                                      final double[] xc, final double[] yc) {
        if (xc == null) {
            return;
        }

        final Stroke oldStroke = g2d.getStroke();
        final Color oldColor = g2d.getColor();
        final AffineTransform oldat = g2d.getTransform();

        g2d.setColor(Color.GREEN);
        g2d.setStroke(STROKE_DEF);

        for (int i = 0, len = xc.length; i < len; i++) {
            g2d.setTransform(oldat);
            g2d.translate(xc[i], yc[i]);
            g2d.draw(shape);
        }

        g2d.setTransform(oldat);
        g2d.setStroke(oldStroke);
        g2d.setColor(oldColor);
    }

    // --- paint details ---
    static final double POINT_DIAM = 10.0;
    static final double POINT_OFF = POINT_DIAM / 2.0 - 0.5;

    static final Stroke STROKE_DEF = new BasicStroke(1.0f);
    static final int COLOR_ALPHA = 150;
    static final Color COLOR_MOVETO = new Color(0, 255, 0, COLOR_ALPHA);
    static final Color COLOR_LINETO_ODD = new Color(0, 0, 255, COLOR_ALPHA);
    static final Color COLOR_LINETO_EVEN = new Color(255, 0, 0, COLOR_ALPHA);

    static final Stroke STROKE_MARLIN_DBG = new BasicStroke(2.0f);
    static final Color COLOR_MARLIN_DBG = new Color(Color.ORANGE.getRGB() & 0x00FFFFFF | 0xC0000000);
    static final Color COLOR_MARLIN_DBG_2 = new Color(Color.CYAN.getRGB() & 0x00FFFFFF | 0xC0000000);

    private final QuadCurve2D.Double QUAD = new QuadCurve2D.Double();
    private final CubicCurve2D.Double CUBIC = new CubicCurve2D.Double();

    private final Line2D.Double LINE = new Line2D.Double();
    private final Ellipse2D.Float ELL_POINT = new Ellipse2D.Float();

    private final double[] coords = new double[6];

    private void paintShapeDetails(final Graphics2D g2d, final Shape shape, final int w, final int h) {
        final Stroke oldStroke = g2d.getStroke();
        final Color oldColor = g2d.getColor();

        g2d.setStroke(STROKE_DEF);
        paintShape(g2d, shape);

        if (showExtra) {
            final MarlinDebugThreadLocal dbgCtx = MarlinDebugThreadLocal.get();
            try {
                // use MarlinDebugThreadLocal to get internal points...
                g2d.setColor(COLOR_MARLIN_DBG);

                for (Point2D p : dbgCtx.getPoints()) {
                    drawPoint(g2d, p.getX(), p.getY());
                }

                g2d.setStroke(STROKE_MARLIN_DBG);

                for (Line2D l : dbgCtx.getSegments()) {
                    g2d.draw(l);
                }

                if (false) {
                    System.out.println("CubicOffsetCurve --- dump ---");

                    for (MarlinDebugThreadLocal.CubicOffsetCurve oc : dbgCtx.getCubicOffsetCurves()) {
                        System.out.println("CubicOffsetCurve: " + oc);
                    }
                    System.out.println("CubicOffsetCurve --- done ---");
                }
                if (!dbgCtx.getCubicOffsetCurves().isEmpty()) {
                    final AffineTransform at = g2d.getTransform();

                    final int max = Math.max(w, h);

                    g2d.drawRect(1, 1, max - 1, max - 1);

                    int n = 0;
                    for (MarlinDebugThreadLocal.CubicOffsetCurve oc : dbgCtx.getCubicOffsetCurves()) {
                        g2d.setColor((n++ % 2 == 0) ? COLOR_MARLIN_DBG : COLOR_MARLIN_DBG_2);
                        drawCurve(g2d, at, oldStroke, oc.getCurve(), oc.getOffset(), max);
                    }
                    g2d.setTransform(at);
                }
            } finally {
                MarlinDebugThreadLocal.release(dbgCtx);
            }
        }

        g2d.setStroke(oldStroke);
        g2d.setColor(oldColor);
    }

    private void paintShape(final Graphics2D g2d, final Shape shape) {
        final Color oldColor = g2d.getColor();

        int nOp = 0;
        double sx0 = 0, sy0 = 0, x0 = 0, y0 = 0;

        for (final PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    g2d.setColor(COLOR_MOVETO);
                    drawPoint(g2d, coords[0], coords[1]);
                    x0 = coords[0];
                    y0 = coords[1];
                    sx0 = x0;
                    sy0 = y0;
                    break;
                case PathIterator.SEG_LINETO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    drawPoint(g2d, coords[0], coords[1]);

                    drawLine(g2d, x0, y0, coords[0], coords[1]);
                    x0 = coords[0];
                    y0 = coords[1];
                    break;
                case PathIterator.SEG_QUADTO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    if (showExtra && paintDetailsTangent) {
                        drawPoint(g2d, coords[0], coords[1]);
                        drawPoint(g2d, coords[2], coords[3]);
                    }

                    QUAD.setCurve(x0, y0, coords[0], coords[1], coords[2], coords[3]);
                    g2d.draw(QUAD);

                    if (showExtra && paintDetailsTangent) {
                        drawLine(g2d, x0, y0, coords[0], coords[1]);
                    }
                    x0 = coords[2];
                    y0 = coords[3];
                    nOp++;
                    break;
                case PathIterator.SEG_CUBICTO:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    if (showExtra && paintDetailsTangent) {
                        drawPoint(g2d, coords[0], coords[1]);
                        drawPoint(g2d, coords[2], coords[3]);
                        drawPoint(g2d, coords[4], coords[5]);
                    }

                    CUBIC.setCurve(x0, y0, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    g2d.draw(CUBIC);

                    if (showExtra && paintDetailsTangent) {
                        drawLine(g2d, x0, y0, coords[0], coords[1]);
                        drawLine(g2d, coords[0], coords[1], coords[2], coords[3]);
                        drawLine(g2d, coords[2], coords[3], coords[4], coords[5]);
                    }
                    if (showExtra && paintMidpoint) {
                        // P12
                        final double x12 = (x0 + coords[0]) / 2.0;
                        final double y12 = (y0 + coords[1]) / 2.0;
                        drawPoint(g2d, x12, y12);
                        // P23
                        final double x23 = (coords[0] + coords[2]) / 2.0;
                        final double y23 = (coords[1] + coords[3]) / 2.0;
                        drawPoint(g2d, x23, y23);
                        // P34
                        final double x34 = (coords[2] + coords[4]) / 2.0;
                        final double y34 = (coords[3] + coords[5]) / 2.0;
                        drawPoint(g2d, x34, y34);
                        // P123
                        final double x123 = (x12 + x23) / 2.0;
                        final double y123 = (y12 + y23) / 2.0;
                        drawPoint(g2d, x123, y123);
                        // P234
                        final double x234 = (x23 + x34) / 2.0;
                        final double y234 = (y23 + y34) / 2.0;
                        drawPoint(g2d, x234, y234);
                        // P1234 = midpoint
                        final double x1234 = (x123 + x234) / 2.0;
                        final double y1234 = (y123 + y234) / 2.0;
                        drawPoint(g2d, x1234, y1234);
                        // Pente it draw Line (P123 to P234)
                        drawLine(g2d, x12, y12, x23, y23);
                        drawLine(g2d, x23, y23, x34, y34);
                        drawLine(g2d, x123, y123, x234, y234);
                        drawLine(g2d, x123, y123, x234, y234);
                    }
                    x0 = coords[4];
                    y0 = coords[5];
                    nOp++;
                    break;
                case PathIterator.SEG_CLOSE:
                    g2d.setColor((nOp % 2 == 0) ? COLOR_LINETO_ODD : COLOR_LINETO_EVEN);
                    drawLine(g2d, x0, y0, sx0, sy0);
                    x0 = sx0;
                    y0 = sy0;
                    continue;
                default:
                    System.out.println("unsupported segment type= " + type);
            }
        }
        g2d.setColor(oldColor);
    }

    private void drawCurve(final Graphics2D g2d, final AffineTransform at, final Stroke oldStroke,
                           final CubicCurve2D c, final CubicCurve2D o, final int max) {
        g2d.setTransform(at);
        g2d.setStroke(oldStroke);
        g2d.draw(o);

        g2d.setTransform(at);
        final Rectangle2D bbox = c.getBounds2D();
        // System.out.println("bbox: "+bbox);

        final double bw = bbox.getWidth();
        final double bh = bbox.getHeight();
        final double bm = Math.max(bw, bh);

        final double bx = (bm - bw) / 2.0;
        final double by = (bm - bh) / 2.0;

        final double scale = (max * 1.0) / bm;
        //System.out.println("scale: "+scale);

        // scale = 5.5;
        g2d.translate((bx - bbox.getMinX()) * scale, (by - bbox.getMinY()) * scale);
        g2d.scale(scale, scale);

        g2d.setStroke(new BasicStroke((float) (10.0 / scale)));

        g2d.draw(c);

//        paintShape(g2d, c);
    }

    private void drawPoint(final Graphics2D g2d, final double x, final double y) {
        final AffineTransform at = g2d.getTransform();
        final double scale = Math.sqrt(at.getScaleX() * at.getScaleX() + at.getScaleY() * at.getScaleY());
        // System.out.println("scale: " + scale);
        ELL_POINT.setFrame(x - POINT_OFF / scale, y - POINT_OFF / scale, POINT_DIAM / scale, POINT_DIAM / scale);
        g2d.fill(ELL_POINT);
    }

    private void drawLine(final Graphics2D g2d,
                          final double x1, final double y1, final double x2, final double y2) {
        LINE.setLine(x1, y1, x2, y2);
        g2d.draw(LINE);
    }

}
