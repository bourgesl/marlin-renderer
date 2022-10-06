/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.marlin.pisces;

import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.marlin.ReentrantContext;

/**
 * Marlin renderer debugging context (internal purposes only)
 */
public final class MarlinDebugThreadLocal extends ReentrantContext {

    private static final boolean USE_CTX = MarlinProperties.isDebugThreadLocal();

    private static final int STORAGE_CAPACITY = USE_CTX ? 100 : 0;

    // Thread-local storage:
    private static final ThreadLocal<MarlinDebugThreadLocal> ctxTL = new ThreadLocal<MarlinDebugThreadLocal>();

    public static boolean isEnabled() {
        return USE_CTX && get().isRecording();
    }

    public static MarlinDebugThreadLocal get() {
        MarlinDebugThreadLocal ctx = ctxTL.get();
        if (ctx == null) {
            // create a new MarlinDebugThreadLocal if none is available
            ctx = new MarlinDebugThreadLocal();
            // update thread local reference:
            ctxTL.set(ctx);
        }
        return ctx;
    }

    public static void startRecord() {
        if (USE_CTX) {
            get().setRecording(true);
        }
    }

    public static int iteration() {
        if (USE_CTX) {
            return get().getIteration();
        }
        return 0;
    }

    public static int resetIteration(final int max) {
        if (USE_CTX) {
            final MarlinDebugThreadLocal dbgCtx = get();
            boolean dir = dbgCtx.getDirection();
            if (dbgCtx.getIteration() >= max) {
                dbgCtx.setIteration(max - 1);
                dir = false;
            }
            if (dbgCtx.getIteration() < 0) {
                dbgCtx.setIteration(0);
                dir = true;
            }
            dbgCtx.setDirection(dir);
            return dbgCtx.getIteration();
        }
        return 0;
    }

    public static void stopRecord() {
        if (USE_CTX) {
            get().setRecording(false);
        }
    }

    public static void release(final MarlinDebugThreadLocal ctx) {
        if (USE_CTX) {
            ctx.reset();
        }
    }

    private static void sampleUsage() {
        // start recording Marlin's debug info:
        MarlinDebugThreadLocal.startRecord();
        // do some graphics operations
        // stop recording Marlin's debug info:
        MarlinDebugThreadLocal.stopRecord();

        final MarlinDebugThreadLocal dbgCtx = MarlinDebugThreadLocal.get();
        try {
            // consume points in MarlinDebugThreadLocal ...
            dbgCtx.getPoints();
        } finally {
            MarlinDebugThreadLocal.release(dbgCtx);
        }
    }

    /* members */
    private boolean recording = false;
    private final AtomicInteger iteration = new AtomicInteger();
    private boolean direction = true;
    private final ArrayList<Point2D> points = new ArrayList<>(STORAGE_CAPACITY);
    private final ArrayList<Line2D> segments = new ArrayList<>(STORAGE_CAPACITY);
    private final ArrayList<CubicOffsetCurve> cubicOffsetCurves = new ArrayList<>(STORAGE_CAPACITY);

    private MarlinDebugThreadLocal() {
        super();
    }

    public boolean getDirection() {
        return direction;
    }

    public void setDirection(final boolean dir) {
        direction = dir;
    }

    public int getIteration() {
        return iteration.get();
    }

    public void nextIteration() {
        if (direction) {
            iteration.incrementAndGet();
        } else {
            iteration.decrementAndGet();
        }
    }

    public void setIteration(final int value) {
        iteration.set(value);
    }

    public void reset() {
        points.clear();
        segments.clear();
        cubicOffsetCurves.clear();
    }

    public boolean isRecording() {
        return USE_CTX && recording;
    }

    public void setRecording(boolean recording) {
        this.recording = USE_CTX && recording;
        if (this.recording) {
            nextIteration();
        }
    }

    void addPoint(final double x, final double y) {
        if (isRecording()) {
            points.add(new Point2D.Double(x, y));
        }
    }

    public List<Point2D> getPoints() {
        return points;
    }

    void addSegment(final double x1, final double y1, final double x2, final double y2) {
        if (isRecording()) {
            segments.add(new Line2D.Double(x1, y1, x2, y2));
        }
    }

    public List<Line2D> getSegments() {
        return segments;
    }

    public List<CubicOffsetCurve> getCubicOffsetCurves() {
        return cubicOffsetCurves;
    }

    void addCubicOffsetCurves(final double[] pts, final int off,
                              final double[] offset) {

        if (isRecording()) {
            cubicOffsetCurves.add(new CubicOffsetCurve(pts, off, offset));
        }
    }

    public final static class CubicOffsetCurve {

        public final CubicCurve2D curve = new CubicCurve2D.Double();
        public final CubicCurve2D offset = new CubicCurve2D.Double();

        CubicOffsetCurve(final double[] pts, final int off,
                         final double[] offset) {

            this.curve.setCurve(pts, off);
            this.offset.setCurve(offset, 0);
        }

        public CubicCurve2D getCurve() {
            return curve;
        }

        public CubicCurve2D getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return "CubicOffsetCurve{" + "curve=" + toString(curve)
                    + ", offset=" + toString(offset) + '}';
        }

        public static String toString(final CubicCurve2D c) {
            return "[" + c.getP1() + ", " + c.getCtrlP1() + ", " + c.getCtrlP2() + ", " + c.getP1() + "]";
        }
    }
}
