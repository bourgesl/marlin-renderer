/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

final class CollinearSimplifier implements PathConsumer2D {

    enum SimplifierState {

        Empty, PreviousPoint, PreviousLine
    };
    /* slope precision threshold */
    static final float EPS = 1e-3f; /* LBO: aaime proposed 1e-3f */

    /* members */
    PathConsumer2D delegate;
    SimplifierState state;
    float px1, py1, px2, py2;
    float pslope;

    CollinearSimplifier() {
    }

    public CollinearSimplifier init(PathConsumer2D delegate) {
        this.delegate = delegate;
        this.state = SimplifierState.Empty;
        
        return this; // fluent API
    }

    @Override
    public void pathDone() {
        emitStashedLine();
        delegate.pathDone();
    }

    @Override
    public void closePath() {
        emitStashedLine();
        delegate.closePath();
    }

    @Override
    public long getNativeConsumer() {
        return delegate.getNativeConsumer();
    }

    @Override
    public void quadTo(float x1, float y1, float x2, float y2) {
        emitStashedLine();
        delegate.quadTo(x1, y1, x2, y2);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        emitStashedLine();
        delegate.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public void moveTo(float x, float y) {
        emitStashedLine();
        delegate.moveTo(x, y);
        state = SimplifierState.PreviousPoint;
        px1 = x;
        py1 = y;
    }

    @Override
    public void lineTo(final float x, final float y) {
        if (state == SimplifierState.Empty) {
            delegate.lineTo(x, y);
            state = SimplifierState.PreviousPoint;
            px1 = x;
            py1 = y;
            return;
        }
        if (state == SimplifierState.PreviousPoint) {
            state = SimplifierState.PreviousLine;
            px2 = x;
            py2 = y;
            pslope = getSlope(px1, py1, x, y);
            return;
        }
        /* state is SimplifierState.PreviousLine */
        final float slope = getSlope(px2, py2, x, y);
        // test for collinear
        if ((slope == pslope) || (Math.abs(pslope - slope) < EPS)) {
            /* merge segments */
            px2 = x;
            py2 = y;
            return;
        }
        /* emit previous segment */
        delegate.lineTo(px2, py2);
        px1 = px2;
        py1 = py2;
        px2 = x;
        py2 = y;
        pslope = slope;
    }

    private void emitStashedLine() {
        if (state == SimplifierState.Empty) {
            /* fast return */
            return;
        }
        if (state == SimplifierState.PreviousLine) {
            delegate.lineTo(px2, py2);
        }
        state = SimplifierState.Empty;
    }

    private float getSlope(float x1, float y1, float x2, float y2) {
        if (y2 == y1) {
            return (x2 > x1) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        return (x2 - x1) / (y2 - y1);
    }
}
