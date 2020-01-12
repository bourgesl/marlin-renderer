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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.marlin.ReentrantContext;

/**
 * Marlin renderer debugging context (internal purposes only)
 */
public final class MarlinDebugThreadLocal extends ReentrantContext {

    // Thread-local storage:
    private static final ThreadLocal<MarlinDebugThreadLocal> ctxTL = new ThreadLocal<MarlinDebugThreadLocal>();

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

    public static void release(final MarlinDebugThreadLocal ctx) {
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
