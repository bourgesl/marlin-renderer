/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.marlin;

import sun.awt.geom.PathConsumer2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import sun.java2d.marlin.Helpers.PolyStack;

final class TransformingPathConsumer2D {

    private final RendererContext rdrCtx;

    // recycled ClosedPathDetector instance from detectClosedPath()
    private final ClosedPathDetector   cpDetector;

    // recycled PathConsumer2D instance from wrapPath2d()
    private final Path2DWrapper        wp_Path2DWrapper        = new Path2DWrapper();

    // recycled PathConsumer2D instances from deltaTransformConsumer()
    private final DeltaScaleFilter     dt_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter dt_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled PathConsumer2D instances from inverseDeltaTransformConsumer()
    private final DeltaScaleFilter     iv_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter iv_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled PathTracer instances from tracer...() methods
    private final PathTracer tracerInput      = new PathTracer("[Input]");
    private final PathTracer tracerCPDetector = new PathTracer("ClosedPathDetector");
    private final PathTracer tracerStroker    = new PathTracer("Stroker");

    TransformingPathConsumer2D(final RendererContext rdrCtx) {
        // used by RendererContext
        this.rdrCtx = rdrCtx;
        this.cpDetector = new ClosedPathDetector(rdrCtx);
    }

    PathConsumer2D wrapPath2d(Path2D.Float p2d)
    {
        return wp_Path2DWrapper.init(p2d);
    }

    PathConsumer2D traceInput(PathConsumer2D out) {
        return tracerInput.init(out);
    }

    PathConsumer2D traceClosedPathDetector(PathConsumer2D out) {
        return tracerCPDetector.init(out);
    }

    PathConsumer2D traceStroker(PathConsumer2D out) {
        return tracerStroker.init(out);
    }

    PathConsumer2D detectClosedPath(PathConsumer2D out)
    {
        return cpDetector.init(out);
    }

    PathConsumer2D deltaTransformConsumer(PathConsumer2D out,
                                          AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        final float mxx = (float) at.getScaleX();
        final float mxy = (float) at.getShearX();
        final float myx = (float) at.getShearY();
        final float myy = (float) at.getScaleY();

        if (mxy == 0.0f && myx == 0.0f) {
            if (mxx == 1.0f && myy == 1.0f) {
                return out;
            } else {
                // Scale only
                if (rdrCtx.doClip) {
                    // adjust clip rectangle (ymin, ymax, xmin, xmax):
                    adjustClipScale(rdrCtx.clipRect, mxx, myy);
                }
                return dt_DeltaScaleFilter.init(out, mxx, myy);
            }
        } else {
            if (rdrCtx.doClip) {
                // adjust clip rectangle (ymin, ymax, xmin, xmax):
                adjustClipInverseDelta(rdrCtx.clipRect, mxx, mxy, myx, myy);
            }
            return dt_DeltaTransformFilter.init(out, mxx, mxy, myx, myy);
        }
    }

    private static void adjustClipOffset(final float[] clipRect) {
        clipRect[0] += Renderer.RDR_OFFSET_Y;
        clipRect[1] += Renderer.RDR_OFFSET_Y;
        clipRect[2] += Renderer.RDR_OFFSET_X;
        clipRect[3] += Renderer.RDR_OFFSET_X;
    }

    private static void adjustClipScale(final float[] clipRect,
                                        final float mxx, final float myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaScaleFilter):
        clipRect[0] /= myy;
        clipRect[1] /= myy;
        clipRect[2] /= mxx;
        clipRect[3] /= mxx;
    }

    private static void adjustClipInverseDelta(final float[] clipRect,
                                               final float mxx, final float mxy,
                                               final float myx, final float myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaTransformFilter):
        final float det = mxx * myy - mxy * myx;
        final float imxx =  myy / det;
        final float imxy = -mxy / det;
        final float imyx = -myx / det;
        final float imyy =  mxx / det;

        float xmin, xmax, ymin, ymax;
        float x, y;
        // xmin, ymin:
        x = clipRect[2] * imxx + clipRect[0] * imxy;
        y = clipRect[2] * imyx + clipRect[0] * imyy;

        xmin = xmax = x;
        ymin = ymax = y;

        // xmax, ymin:
        x = clipRect[3] * imxx + clipRect[0] * imxy;
        y = clipRect[3] * imyx + clipRect[0] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        // xmin, ymax:
        x = clipRect[2] * imxx + clipRect[1] * imxy;
        y = clipRect[2] * imyx + clipRect[1] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        // xmax, ymax:
        x = clipRect[3] * imxx + clipRect[1] * imxy;
        y = clipRect[3] * imyx + clipRect[1] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        clipRect[0] = ymin;
        clipRect[1] = ymax;
        clipRect[2] = xmin;
        clipRect[3] = xmax;
    }

    PathConsumer2D inverseDeltaTransformConsumer(PathConsumer2D out,
                                                 AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        float mxx = (float) at.getScaleX();
        float mxy = (float) at.getShearX();
        float myx = (float) at.getShearY();
        float myy = (float) at.getScaleY();

        if (mxy == 0.0f && myx == 0.0f) {
            if (mxx == 1.0f && myy == 1.0f) {
                return out;
            } else {
                return iv_DeltaScaleFilter.init(out, 1.0f/mxx, 1.0f/myy);
            }
        } else {
            final float det = mxx * myy - mxy * myx;
            return iv_DeltaTransformFilter.init(out,
                                                myy / det,
                                               -mxy / det,
                                               -myx / det,
                                                mxx / det);
        }
    }

    static final class DeltaScaleFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float sx, sy;

        DeltaScaleFilter() {}

        DeltaScaleFilter init(PathConsumer2D out,
                              float mxx, float myy)
        {
            this.out = out;
            sx = mxx;
            sy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * sx, y0 * sy);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * sx, y1 * sy);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * sx, y1 * sy,
                       x2 * sx, y2 * sy);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * sx, y1 * sy,
                        x2 * sx, y2 * sy,
                        x3 * sx, y3 * sy);
        }

        @Override
        public void closePath() {
            out.closePath();
        }

        @Override
        public void pathDone() {
            out.pathDone();
        }

        @Override
        public long getNativeConsumer() {
            return 0;
        }
    }

    static final class DeltaTransformFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float mxx, mxy, myx, myy;

        DeltaTransformFilter() {}

        DeltaTransformFilter init(PathConsumer2D out,
                                  float mxx, float mxy,
                                  float myx, float myy)
        {
            this.out = out;
            this.mxx = mxx;
            this.mxy = mxy;
            this.myx = myx;
            this.myy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * mxx + y0 * mxy,
                       x0 * myx + y0 * myy);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy,
                       x2 * mxx + y2 * mxy,
                       x2 * myx + y2 * myy);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * mxx + y1 * mxy,
                        x1 * myx + y1 * myy,
                        x2 * mxx + y2 * mxy,
                        x2 * myx + y2 * myy,
                        x3 * mxx + y3 * mxy,
                        x3 * myx + y3 * myy);
        }

        @Override
        public void closePath() {
            out.closePath();
        }

        @Override
        public void pathDone() {
            out.pathDone();
        }

        @Override
        public long getNativeConsumer() {
            return 0;
        }
    }

    static final class Path2DWrapper implements PathConsumer2D {
        private Path2D.Float p2d;

        Path2DWrapper() {}

        Path2DWrapper init(Path2D.Float p2d) {
            this.p2d = p2d;
            return this;
        }

        @Override
        public void moveTo(float x0, float y0) {
            p2d.moveTo(x0, y0);
        }

        @Override
        public void lineTo(float x1, float y1) {
            p2d.lineTo(x1, y1);
        }

        @Override
        public void closePath() {
            p2d.closePath();
        }

        @Override
        public void pathDone() {}

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            p2d.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x1, float y1, float x2, float y2) {
            p2d.quadTo(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class ClosedPathDetector implements PathConsumer2D {

        private final RendererContext rdrCtx;
        private final PolyStack stack;

        private PathConsumer2D out;

        ClosedPathDetector(final RendererContext rdrCtx) {
            this.rdrCtx = rdrCtx;
            this.stack = (rdrCtx.stats != null) ?
                new PolyStack(rdrCtx,
                        rdrCtx.stats.stat_cpd_polystack_types,
                        rdrCtx.stats.stat_cpd_polystack_curves,
                        rdrCtx.stats.hist_cpd_polystack_curves,
                        rdrCtx.stats.stat_array_cpd_polystack_curves,
                        rdrCtx.stats.stat_array_cpd_polystack_types)
                : new PolyStack(rdrCtx);
        }

        ClosedPathDetector init(PathConsumer2D out) {
            this.out = out;
            return this; // fluent API
        }

        /**
         * Disposes this instance:
         * clean up before reusing this instance
         */
        void dispose() {
            stack.dispose();
        }

        @Override
        public void pathDone() {
            // previous path is not closed:
            finish(false);
            out.pathDone();

            // TODO: fix possible leak if exception happened
            // Dispose this instance:
            dispose();
        }

        @Override
        public void closePath() {
            // path is closed
            finish(true);
            out.closePath();
        }

        @Override
        public void moveTo(float x0, float y0) {
            // previous path is not closed:
            finish(false);
            out.moveTo(x0, y0);
        }

        private void finish(final boolean closed) {
            rdrCtx.closedPath = closed;
            stack.pullAll(out);
        }

        @Override
        public void lineTo(float x1, float y1) {
            stack.pushLine(x1, y1);
        }

        @Override
        public void curveTo(float x3, float y3,
                            float x2, float y2,
                            float x1, float y1)
        {
            stack.pushCubic(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x2, float y2, float x1, float y1) {
            stack.pushQuad(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class PathTracer implements PathConsumer2D {
        private final String prefix;
        private PathConsumer2D out;

        PathTracer(String name) {
            this.prefix = name + ": ";
        }

        PathTracer init(PathConsumer2D out) {
            this.out = out;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            log("moveTo (" + x0 + ", " + y0 + ')');
            out.moveTo(x0, y0);
        }

        @Override
        public void lineTo(float x1, float y1) {
            log("lineTo (" + x1 + ", " + y1 + ')');
            out.lineTo(x1, y1);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            log("curveTo P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2  + ") P3(" + x3 + ", " + y3 + ')');
            out.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x1, float y1, float x2, float y2) {
            log("quadTo P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2  + ')');
            out.quadTo(x1, y1, x2, y2);
        }

        @Override
        public void closePath() {
            log("closePath");
            out.closePath();
        }

        @Override
        public void pathDone() {
            log("pathDone");
            out.pathDone();
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
