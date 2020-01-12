/*******************************************************************************
 * TODO
 ******************************************************************************/
package org.marlin.pisces;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.marlin.ReentrantContext;

/**
 *
 */
public final class MarlinDebugThreadLocal extends ReentrantContext {

    // Thread-local storage:
    private static final ThreadLocal<MarlinDebugThreadLocal> ctxTL = new ThreadLocal<MarlinDebugThreadLocal>();

    public static MarlinDebugThreadLocal get() {
        MarlinDebugThreadLocal ctx = ctxTL.get();
        if (ctx == null) {
            // create a new MarlinDebugThreadLocal if none is available
            ctx = new MarlinDebugThreadLocal();
//            System.out.println("create new: " + ctx);
            // update thread local reference:
            ctxTL.set(ctx);
        }
//        System.out.println("get: " + ctx);
        return ctx;
    }

    public static void release(final MarlinDebugThreadLocal ctx) {
//        System.out.println("release: " + ctx);
        ctx.reset();
    }

    public static void sampleUsage() {
        final MarlinDebugThreadLocal dbgCtx = get();
        try {
            // use MarlinDebugThreadLocal ...
            dbgCtx.addPoint(0, 0);
        } finally {
            release(dbgCtx);
        }
    }

    /* members */
    private final ArrayList<Point2D> points = new ArrayList<Point2D>(10);

    private MarlinDebugThreadLocal() {
        super();
    }

    public void reset() {
        points.clear();
    }

    public List<Point2D> getPoints() {
        return points;
    }

    public void addPoint(final double x, final double y) {
        points.add(new Point2D.Double(x, y));
    }

}
