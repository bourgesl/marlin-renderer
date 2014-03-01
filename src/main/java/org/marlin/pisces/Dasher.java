/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import sun.awt.geom.PathConsumer2D;
/**
 * The <code>Dasher</code> class takes a series of linear commands
 * (<code>moveTo</code>, <code>lineTo</code>, <code>close</code> and
 * <code>end</code>) and breaks them into smaller segments according to a
 * dash pattern array and a starting dash phase.
 *
 * <p> Issues: in J2Se, a zero length dash segment as drawn as a very
 * short dash, whereas Pisces does not draw anything.  The PostScript
 * semantics are unclear.
 *
 */
final class Dasher implements sun.awt.geom.PathConsumer2D, PiscesConst {
    final static int recLimit = 4;
    final static float ERR = 0.01f;
    final static float minTincrement = 1f / (1 << recLimit);

    /* members */
    private PathConsumer2D out;
    private float[] dash;
    private int dashLen;
    private float startPhase;
    private boolean startDashOn;
    private int startIdx;

    private boolean starting;
    private boolean needsMoveTo;

    private int idx;
    private boolean dashOn;
    private float phase;

    private float sx, sy;
    private float x0, y0;

    // temporary storage for the current curve
    private final float[] curCurvepts;

    /** per-thread renderer context */
    final RendererContext rdrCtx;
    
    /* dashes array (dirty) */
    final float[] dashes_initial = new float[INITIAL_ARRAY];

    /* flag to recycle dash array copy */
    boolean recycleDashes;

    /* per-thread initial arrays (large enough to satisfy most usages */
    private final float[] firstSegmentsBuffer_initial = new float[INITIAL_ARRAY + 1]; // +1 to avoid recycling in Helpers.widenArray()

    /**
     * Constructs a <code>Dasher</code>.
     * @param rdrCtx per-thread renderer context
     */
    Dasher(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        firstSegmentsBuffer = firstSegmentsBuffer_initial;

        // we need curCurvepts to be able to contain 2 curves because when
        // dashing curves, we need to subdivide it
        curCurvepts = new float[8 * 2];
    }
    
    /**
     * Initialize the <code>Dasher</code>.
     *
     * @param out an output <code>PathConsumer2D</code>.
     * @param dash an array of <code>float</code>s containing the dash pattern
     * @param phase a <code>float</code> containing the dash phase
     * @return this instance
     */
    Dasher init(final PathConsumer2D out, float[] dash, int dashLen, float phase, boolean recycleDashes) {
        if (phase < 0) {
            throw new IllegalArgumentException("phase < 0 !");
        }
        this.out = out;

        // Normalize so 0 <= phase < dash[0]
        int idx = 0;
        dashOn = true;
        float d;
        while (phase >= (d = dash[idx])) {
            phase -= d;
            idx = (idx + 1) % dashLen;
            dashOn = !dashOn;
        }

        this.dash = dash;
        this.dashLen = dashLen;
        this.startPhase = this.phase = phase;
        this.startDashOn = dashOn;
        this.startIdx = idx;
        this.starting = true;
        needsMoveTo = false;
        
        firstSegUsed = 0;
        firstSegidx = 0;
        
        this.recycleDashes = recycleDashes;
        
        return this; // fluent API
    }

    /**
     * Disposes this dasher:
     * clean up before reusing this instance
     */
    void dispose() {
        // Return arrays:
        if (this.recycleDashes && dash != dashes_initial) {
            rdrCtx.putFloatArray(dash, 0, dashLen);
        }
        
        if (firstSegmentsBuffer != firstSegmentsBuffer_initial) {
            rdrCtx.putFloatArray(firstSegmentsBuffer, 0, firstSegUsed);
            firstSegmentsBuffer = firstSegmentsBuffer_initial;
        }
        
        if (doCleanDirty) {
            // LBO: keep curCurvepts and firstSegmentsBuffer dirty
            Arrays.fill(curCurvepts, 0);
            Arrays.fill(firstSegmentsBuffer, 0, firstSegUsed, 0);
        }
    }

    public void moveTo(float x0, float y0) {
        if (firstSegidx > 0) {
            out.moveTo(sx, sy);
            emitFirstSegments();
        }
        needsMoveTo = true;
        this.idx = startIdx;
        this.dashOn = this.startDashOn;
        this.phase = this.startPhase;
        this.sx = this.x0 = x0;
        this.sy = this.y0 = y0;
        this.starting = true;
    }

    private void emitSeg(float[] buf, int off, int type) {
        switch (type) {
        case 8:
            out.curveTo(buf[off+0], buf[off+1],
                        buf[off+2], buf[off+3],
                        buf[off+4], buf[off+5]);
            break;
        case 6:
            out.quadTo(buf[off+0], buf[off+1],
                       buf[off+2], buf[off+3]);
            break;
        case 4:
            out.lineTo(buf[off], buf[off+1]);
        }
    }

    private void emitFirstSegments() {
        final float[] fSegBuf = firstSegmentsBuffer;
        
        for (int i = 0; i < firstSegidx; ) {
            int type = (int)fSegBuf[i];
            emitSeg(fSegBuf, i + 1, type);
            i += (type - 1);
        }
        firstSegidx = 0;
    }
    // We don't emit the first dash right away. If we did, caps would be
    // drawn on it, but we need joins to be drawn if there's a closePath()
    // So, we store the path elements that make up the first dash in the
    // buffer below.
    private float[] firstSegmentsBuffer; // dynamic array
    private int firstSegidx;
    // max used mark
    private int firstSegUsed;
    
    // precondition: pts must be in relative coordinates (relative to x0,y0)
    // fullCurve is true iff the curve in pts has not been split.
    private void goTo(float[] pts, int off, final int type) {
        float x = pts[off + type - 4];
        float y = pts[off + type - 3];
        if (dashOn) {
            if (starting) {
                firstSegmentsBuffer = Helpers.widenArray(rdrCtx, firstSegmentsBuffer,
                                      firstSegidx, type - 2, firstSegUsed);
                firstSegmentsBuffer[firstSegidx++] = type;
                System.arraycopy(pts, off, firstSegmentsBuffer, firstSegidx, type - 2);
                firstSegidx += type - 2;
                // update used mark
                if (firstSegidx > firstSegUsed) { firstSegUsed = firstSegidx; }
            } else {
                if (needsMoveTo) {
                    out.moveTo(x0, y0);
                    needsMoveTo = false;
                }
                emitSeg(pts, off, type);
            }
        } else {
            starting = false;
            needsMoveTo = true;
        }
        this.x0 = x;
        this.y0 = y;
    }

    public void lineTo(float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;

        float len = (float) Math.sqrt(dx*dx + dy*dy);

        if (len == 0) {
            return;
        }

        // The scaling factors needed to get the dx and dy of the
        // transformed dash segments.
        final float cx = dx / len;
        final float cy = dy / len;

        final float[] _curCurvepts = curCurvepts;
        final float[] _dash = dash;
        
        float leftInThisDashSegment;
        float dashdx, dashdy, p;
        
        while (true) {
            leftInThisDashSegment = _dash[idx] - phase;
            
            if (len <= leftInThisDashSegment) {
                _curCurvepts[0] = x1;
                _curCurvepts[1] = y1;
                goTo(_curCurvepts, 0, 4);
                
                // Advance phase within current dash segment
                phase += len;
                if (len == leftInThisDashSegment) {
                    phase = 0f;
                    idx = (idx + 1) % dashLen;
                    dashOn = !dashOn;
                }
                return;
            }

            dashdx = _dash[idx] * cx;
            dashdy = _dash[idx] * cy;
            
            if (phase == 0) {
                _curCurvepts[0] = x0 + dashdx;
                _curCurvepts[1] = y0 + dashdy;
            } else {
                p = leftInThisDashSegment / _dash[idx];
                _curCurvepts[0] = x0 + p * dashdx;
                _curCurvepts[1] = y0 + p * dashdy;
            }

            goTo(_curCurvepts, 0, 4);

            len -= leftInThisDashSegment;
            // Advance to next dash segment
            idx = (idx + 1) % dashLen;
            dashOn = !dashOn;
            phase = 0;
        }
    }

    // shared instance in Dasher
    private final LengthIterator li = new LengthIterator();

    // preconditions: curCurvepts must be an array of length at least 2 * type,
    // that contains the curve we want to dash in the first type elements
    private void somethingTo(int type) {
        if (pointCurve(curCurvepts, type)) {
            return;
        }
        li.initializeIterationOnCurve(curCurvepts, type);

        int curCurveoff = 0; // initially the current curve is at curCurvepts[0...type]
        float lastSplitT = 0;
        float t = 0;
        float leftInThisDashSegment = dash[idx] - phase;
        while ((t = li.next(leftInThisDashSegment)) < 1) {
            if (t != 0) {
                Helpers.subdivideAt((t - lastSplitT) / (1 - lastSplitT),
                                    curCurvepts, curCurveoff,
                                    curCurvepts, 0,
                                    curCurvepts, type, type);
                lastSplitT = t;
                goTo(curCurvepts, 2, type);
                curCurveoff = type;
            }
            // Advance to next dash segment
            idx = (idx + 1) % dashLen;
            dashOn = !dashOn;
            phase = 0;
            leftInThisDashSegment = dash[idx];
        }
        goTo(curCurvepts, curCurveoff+2, type);
        phase += li.lastSegLen();
        if (phase >= dash[idx]) {
            phase = 0f;
            idx = (idx + 1) % dashLen;
            dashOn = !dashOn;
        }
        // LBO: reset LengthIterator:
        li.reset();
    }

    private static boolean pointCurve(float[] curve, int type) {
        for (int i = 2; i < type; i++) {
            if (curve[i] != curve[i-2]) {
                return false;
            }
        }
        return true;
    }

    // Objects of this class are used to iterate through curves. They return
    // t values where the left side of the curve has a specified length.
    // It does this by subdividing the input curve until a certain error
    // condition has been met. A recursive subdivision procedure would
    // return as many as 1<<limit curves, but this is an iterator and we
    // don't need all the curves all at once, so what we carry out a
    // lazy inorder traversal of the recursion tree (meaning we only move
    // through the tree when we need the next subdivided curve). This saves
    // us a lot of memory because at any one time we only need to store
    // limit+1 curves - one for each level of the tree + 1.
    // NOTE: the way we do things here is not enough to traverse a general
    // tree; however, the trees we are interested in have the property that
    // every non leaf node has exactly 2 children
    final static class LengthIterator {
        private enum Side {LEFT, RIGHT};
        // Holds the curves at various levels of the recursion. The root
        // (i.e. the original curve) is at recCurveStack[0] (but then it
        // gets subdivided, the left half is put at 1, so most of the time
        // only the right half of the original curve is at 0)
        private final float[][] recCurveStack; // dirty
        // sides[i] indicates whether the node at level i+1 in the path from
        // the root to the current leaf is a left or right child of its parent.
        private final Side[] sides; // dirty
        private int curveType;
        // lastT and nextT delimit the current leaf.
        private float nextT;
        private float lenAtNextT;
        private float lastT;
        private float lenAtLastT;
        private float lenAtLastSplit;
        private float lastSegLen;
        // the current level in the recursion tree. 0 is the root. limit
        // is the deepest possible leaf.
        private int recLevel;
        private boolean done;

        // the lengths of the lines of the control polygon. Only its first
        // curveType/2 - 1 elements are valid. This is an optimization. See
        // next(float) for more detail.
        private final float[] curLeafCtrlPolyLengths = new float[3];

        LengthIterator() {
            this.recCurveStack = new float[recLimit + 1][8];
            this.sides = new Side[recLimit];
            // if any methods are called without first initializing this object on
            // a curve, we want it to fail ASAP.
            this.nextT = Float.MAX_VALUE;
            this.lenAtNextT = Float.MAX_VALUE;
            this.lenAtLastSplit = Float.MIN_VALUE;
            this.recLevel = Integer.MIN_VALUE;
            this.lastSegLen = Float.MAX_VALUE;
            this.done = true;
        }

        /**
         * Reset this LengthIterator.
         */
        void reset() {
            // LBO: keep liData dirty 
            // as it appears not useful to reset data:
            if (doCleanDirty) {
                final int recLimit = recCurveStack.length - 1;
                for (int i = recLimit; i >= 0; i--) {
                    Arrays.fill(recCurveStack[i], 0, 8, 0);
                }
                Arrays.fill(sides, 0, recLimit, Side.LEFT);
                Arrays.fill(curLeafCtrlPolyLengths, 0, 3, 0);
                Arrays.fill(nextRoots, 0, 4, 0);
                Arrays.fill(flatLeafCoefCache, 0, 4, 0);
            }
            // Ensure the cache is
            // invalid when it's third element is negative, since in any
            // valid flattened curve, this would be >= 0.
            flatLeafCoefCache[2] = -1f;
        }

        void initializeIterationOnCurve(float[] pts, int type) {
            System.arraycopy(pts, 0, recCurveStack[0], 0, type);
            this.curveType = type;
            this.recLevel = 0;
            this.lastT = 0;
            this.lenAtLastT = 0;
            this.nextT = 0;
            this.lenAtNextT = 0;
            goLeft(); // initializes nextT and lenAtNextT properly
            this.lenAtLastSplit = 0;
            if (recLevel > 0) {
                this.sides[0] = Side.LEFT;
                this.done = false;
            } else {
                // the root of the tree is a leaf so we're done.
                this.sides[0] = Side.RIGHT;
                this.done = true;
            }
            this.lastSegLen = 0;
        }

        // 0 == false, 1 == true, -1 == invalid cached value.
        private int cachedHaveLowAcceleration = -1;

        private boolean haveLowAcceleration(float err) {
            if (cachedHaveLowAcceleration == -1) {
                final float len1 = curLeafCtrlPolyLengths[0];
                final float len2 = curLeafCtrlPolyLengths[1];
                // the test below is equivalent to !within(len1/len2, 1, err).
                // It is using a multiplication instead of a division, so it
                // should be a bit faster.
                if (!Helpers.within(len1, len2, err*len2)) {
                    cachedHaveLowAcceleration = 0;
                    return false;
                }
                if (curveType == 8) {
                    final float len3 = curLeafCtrlPolyLengths[2];
                    // if len1 is close to 2 and 2 is close to 3, that probably
                    // means 1 is close to 3 so the second part of this test might
                    // not be needed, but it doesn't hurt to include it.
                    if (!(Helpers.within(len2, len3, err*len3) &&
                          Helpers.within(len1, len3, err*len3))) {
                        cachedHaveLowAcceleration = 0;
                        return false;
                    }
                }
                cachedHaveLowAcceleration = 1;
                return true;
            }

            return (cachedHaveLowAcceleration == 1);
        }

        // we want to avoid allocations/gc so we keep this array so we
        // can put roots in it,
        private final float[] nextRoots = new float[4];

        // caches the coefficients of the current leaf in its flattened
        // form (see inside next() for what that means). The cache is
        // invalid when it's third element is negative, since in any
        // valid flattened curve, this would be >= 0.
        private final float[] flatLeafCoefCache = new float[]{0f, 0f, -1f, 0f};
        
        // returns the t value where the remaining curve should be split in
        // order for the left subdivided curve to have length len. If len
        // is >= than the length of the uniterated curve, it returns 1.
        float next(final float len) {
            final float targetLength = lenAtLastSplit + len;
            while (lenAtNextT < targetLength) {
                if (done) {
                    lastSegLen = lenAtNextT - lenAtLastSplit;
                    return 1;
                }
                goToNextLeaf();
            }
            lenAtLastSplit = targetLength;
            final float leaflen = lenAtNextT - lenAtLastT;
            float t = (targetLength - lenAtLastT) / leaflen;

            // cubicRootsInAB is a fairly expensive call, so we just don't do it
            // if the acceleration in this section of the curve is small enough.
            if (!haveLowAcceleration(0.05f)) {
                // We flatten the current leaf along the x axis, so that we're
                // left with a, b, c which define a 1D Bezier curve. We then
                // solve this to get the parameter of the original leaf that
                // gives us the desired length.
                final float[] _flatLeafCoefCache = flatLeafCoefCache;
                
                if (_flatLeafCoefCache[2] < 0) {
                    float x = 0f + curLeafCtrlPolyLengths[0],
                            y = x + curLeafCtrlPolyLengths[1];
                    if (curveType == 8) {
                        float z = y + curLeafCtrlPolyLengths[2];
                        _flatLeafCoefCache[0] = 3f * (x - y) + z;
                        _flatLeafCoefCache[1] = 3f * (y - 2f * x);
                        _flatLeafCoefCache[2] = 3f * x;
                        _flatLeafCoefCache[3] = -z;
                    } else if (curveType == 6) {
                        _flatLeafCoefCache[0] = 0f;
                        _flatLeafCoefCache[1] = y - 2f * x;
                        _flatLeafCoefCache[2] = 2f * x;
                        _flatLeafCoefCache[3] = -y;
                    }
                }
                float a = _flatLeafCoefCache[0];
                float b = _flatLeafCoefCache[1];
                float c = _flatLeafCoefCache[2];
                float d = t * _flatLeafCoefCache[3];

                // we use cubicRootsInAB here, because we want only roots in 0, 1,
                // and our quadratic root finder doesn't filter, so it's just a
                // matter of convenience.
                int n = Helpers.cubicRootsInAB(a, b, c, d, nextRoots, 0, 0, 1);
                if (n == 1 && !Float.isNaN(nextRoots[0])) {
                    t = nextRoots[0];
                }
            }
            // t is relative to the current leaf, so we must make it a valid parameter
            // of the original curve.
            t = t * (nextT - lastT) + lastT;
            if (t >= 1) {
                t = 1;
                done = true;
            }
            // even if done = true, if we're here, that means targetLength
            // is equal to, or very, very close to the total length of the
            // curve, so lastSegLen won't be too high. In cases where len
            // overshoots the curve, this method will exit in the while
            // loop, and lastSegLen will still be set to the right value.
            lastSegLen = len;
            return t;
        }

        float lastSegLen() {
            return lastSegLen;
        }

        // go to the next leaf (in an inorder traversal) in the recursion tree
        // preconditions: must be on a leaf, and that leaf must not be the root.
        private void goToNextLeaf() {
            // We must go to the first ancestor node that has an unvisited
            // right child.
            int _recLevel = recLevel;
            final Side[] _sides = sides;
            
            _recLevel--;
            while(_sides[_recLevel] == Side.RIGHT) {
                if (_recLevel == 0) {
                    recLevel = 0;
                    done = true;
                    return;
                }
                _recLevel--;
            }

            _sides[_recLevel] = Side.RIGHT;
            System.arraycopy(recCurveStack[_recLevel], 0, recCurveStack[_recLevel+1], 0, curveType);
            _recLevel++;
            
            recLevel = _recLevel;
            goLeft();
        }

        // go to the leftmost node from the current node. Return its length.
        private void goLeft() {
            float len = onLeaf();
            if (len >= 0) {
                lastT = nextT;
                lenAtLastT = lenAtNextT;
                nextT += (1 << (recLimit - recLevel)) * minTincrement;
                lenAtNextT += len;
                // invalidate caches
                flatLeafCoefCache[2] = -1;
                cachedHaveLowAcceleration = -1;
            } else {
                Helpers.subdivide(recCurveStack[recLevel], 0,
                                  recCurveStack[recLevel+1], 0,
                                  recCurveStack[recLevel], 0, curveType);
                sides[recLevel] = Side.LEFT;
                recLevel++;
                goLeft();
            }
        }

        // this is a bit of a hack. It returns -1 if we're not on a leaf, and
        // the length of the leaf if we are on a leaf.
        private float onLeaf() {
            float[] curve = recCurveStack[recLevel];
            float polyLen = 0;

            float x0 = curve[0], y0 = curve[1];
            for (int i = 2; i < curveType; i += 2) {
                final float x1 = curve[i], y1 = curve[i+1];
                final float len = Helpers.linelen(x0, y0, x1, y1);
                polyLen += len;
                curLeafCtrlPolyLengths[i/2 - 1] = len;
                x0 = x1;
                y0 = y1;
            }

            final float lineLen = Helpers.linelen(curve[0], curve[1], curve[curveType-2], curve[curveType-1]);
            if (polyLen - lineLen < ERR || recLevel == recLimit) {
                return (polyLen + lineLen)/2;
            }
            return -1;
        }
    }

    @Override
    public void curveTo(float x1, float y1,
                        float x2, float y2,
                        float x3, float y3)
    {
        final float[] _curCurvepts = curCurvepts;
        _curCurvepts[0] = x0;        _curCurvepts[1] = y0;
        _curCurvepts[2] = x1;        _curCurvepts[3] = y1;
        _curCurvepts[4] = x2;        _curCurvepts[5] = y2;
        _curCurvepts[6] = x3;        _curCurvepts[7] = y3;
        somethingTo(8);
    }

    @Override
    public void quadTo(float x1, float y1, float x2, float y2) {
        final float[] _curCurvepts = curCurvepts;
        _curCurvepts[0] = x0;        _curCurvepts[1] = y0;
        _curCurvepts[2] = x1;        _curCurvepts[3] = y1;
        _curCurvepts[4] = x2;        _curCurvepts[5] = y2;
        somethingTo(6);
    }

    public void closePath() {
        lineTo(sx, sy);
        if (firstSegidx > 0) {
            if (!dashOn || needsMoveTo) {
                out.moveTo(sx, sy);
            }
            emitFirstSegments();
        }
        moveTo(sx, sy);
    }

    public void pathDone() {
        if (firstSegidx > 0) {
            out.moveTo(sx, sy);
            emitFirstSegments();
        }
        out.pathDone();

        // Dispose this instance:
        dispose();
    }

    @Override
    public long getNativeConsumer() {
        throw new InternalError("Dasher does not use a native consumer");
    }
}

