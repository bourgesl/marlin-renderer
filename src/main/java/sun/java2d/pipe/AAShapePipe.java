/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
package sun.java2d.pipe;

import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ConcurrentLinkedQueue;
import sun.awt.SunHints;
import sun.java2d.SunGraphics2D;

/**
 * This class is used to convert raw geometry into 8-bit alpha tiles
 * using an AATileGenerator for application by the next stage of
 * the pipeline.
 * This class sets up the Generator and computes the alpha tiles
 * and then passes them on to a CompositePipe object for painting.
 */
public final class AAShapePipe
    implements ShapeDrawPipe, ParallelogramPipe
{
    static final RenderingEngine renderengine = RenderingEngine.getInstance();

    // Per-thread TileState (~1K very small so do not use any Weak Reference)
    private static final ReentrantThreadLocal<TileState> tileStateThreadLocal =
            new ReentrantThreadLocal<TileState>() {
        @Override
        protected TileState initialValue() {
            return new TileState();
        }
    };

    final CompositePipe outpipe;

    public AAShapePipe(CompositePipe pipe) {
        outpipe = pipe;
    }

    @Override
    public void draw(SunGraphics2D sg, Shape s) {
        final BasicStroke bs;

        if (sg.stroke instanceof BasicStroke) {
            bs = (BasicStroke) sg.stroke;
        } else {
            s = sg.stroke.createStrokedShape(s);
            bs = null;
        }

        renderPath(sg, s, bs);
    }

    @Override
    public void fill(SunGraphics2D sg, Shape s) {
        renderPath(sg, s, null);
    }

    @Override
    public void fillParallelogram(SunGraphics2D sg,
                                  double ux1, double uy1,
                                  double ux2, double uy2,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2)
    {
        final TileState ts = tileStateThreadLocal.get();
        try {
            final int[] abox = ts.abox;

            final AATileGenerator aatg =
                renderengine.getAATileGenerator(x, y, dx1, dy1, dx2, dy2, 0, 0,
                                                sg.getCompClip(), abox);
            if (aatg != null) {
                renderTiles(sg, ts.computeBBox(ux1, uy1, ux2, uy2),
                            aatg, abox, ts);
            }
        } finally {
            tileStateThreadLocal.restore(ts);
        }
    }

    @Override
    public void drawParallelogram(SunGraphics2D sg,
                                  double ux1, double uy1,
                                  double ux2, double uy2,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2,
                                  double lw1, double lw2)
    {
        final TileState ts = tileStateThreadLocal.get();
        try {
            final int[] abox = ts.abox;

            final AATileGenerator aatg =
                renderengine.getAATileGenerator(x, y, dx1, dy1, dx2, dy2, lw1,
                                                lw2, sg.getCompClip(), abox);
            if (aatg != null) {
                // Note that bbox is of the original shape, not the wide path.
                // This is appropriate for handing to Paint methods...
                renderTiles(sg, ts.computeBBox(ux1, uy1, ux2, uy2),
                            aatg, abox, ts);
            }
        } finally {
            tileStateThreadLocal.restore(ts);
        }
    }

    public void renderPath(SunGraphics2D sg, Shape s, BasicStroke bs) {
        final boolean adjust = (bs != null &&
                          sg.strokeHint != SunHints.INTVAL_STROKE_PURE);
        final boolean thin = (sg.strokeState <= SunGraphics2D.STROKE_THINDASHED);

        final TileState ts = tileStateThreadLocal.get();
        try {
            final int[] abox = ts.abox;

            final AATileGenerator aatg =
                renderengine.getAATileGenerator(s, sg.transform, sg.getCompClip(),
                                                bs, thin, adjust, abox);
            if (aatg != null) {
                renderTiles(sg, s, aatg, abox, ts);
            }
        } finally {
            tileStateThreadLocal.restore(ts);
        }
    }

    public void renderTiles(SunGraphics2D sg, Shape s,
                            final AATileGenerator aatg,
                            final int[] abox, final TileState ts)
    {
        Object context = null;
        try {
            // reentrance: outpipe may also use AAShapePipe:
            context = outpipe.startSequence(sg, s,
                                            ts.computeDevBox(abox),
                                            abox);

            // copy of int[] abox as local variables for performance:
            final int bx = abox[0];
            final int by = abox[1];
            final int bw = abox[2];
            final int bh = abox[3];

            final int tw = aatg.getTileWidth();
            final int th = aatg.getTileHeight();

            // get tile from thread local storage:
            final byte[] alpha = ts.getAlphaTile(tw * th);
            byte[] atile;

            for (int y = by; y < bh; y += th) {
                final int h = Math.min(th, bh - y);

                for (int x = bx; x < bw; x += tw) {
                    final int w = Math.min(tw, bw - x);

                    final int a = aatg.getTypicalAlpha();

                    if (a == 0x00 || !outpipe.needTile(context, x, y, w, h)) {
                        aatg.nextTile();
                        outpipe.skipTile(context, x, y);
                        continue;
                    }
                    if (a == 0xff) {
                        atile = null;
                        aatg.nextTile();
                    } else {
                        atile = alpha;
                        aatg.getAlpha(alpha, 0, tw);
                    }

                    outpipe.renderPathTile(context, atile, 0, tw, x, y, w, h);
                }
            }
        } finally {
            aatg.dispose();
            if (context != null) {
                outpipe.endSequence(context);
            }
        }
    }

    // Tile state used by AAShapePipe
    static final class TileState extends ReentrantContext {
        // cached tile (32 x 32 tile by default)
        private byte[] theTile = new byte[32 * 32];
        // dirty aabox array
        final int[] abox = new int[4];
        // dirty bbox rectangle
        private final Rectangle dev = new Rectangle();
        // dirty bbox rectangle2D.Double
        private final Rectangle2D.Double bbox2D = new Rectangle2D.Double();

        byte[] getAlphaTile(int len) {
            byte[] t = theTile;
            if (t.length < len) {
                // create a larger tile and may free current theTile (too small)
                theTile = t = new byte[len];
            }
            return t;
        }

        Rectangle computeDevBox(final int[] abox) {
            final Rectangle box = this.dev;
            box.x = abox[0];
            box.y = abox[1];
            box.width = abox[2] - abox[0];
            box.height = abox[3] - abox[1];
            return box;
        }

        Rectangle2D computeBBox(double ux1, double uy1,
                                double ux2, double uy2)
        {
            if ((ux2 -= ux1) < 0.0) {
                ux1 += ux2;
                ux2 = -ux2;
            }
            if ((uy2 -= uy1) < 0.0) {
                uy1 += uy2;
                uy2 = -uy2;
            }
            final Rectangle2D.Double box = this.bbox2D;
            box.x = ux1;
            box.y = uy1;
            box.width = ux2;
            box.height = uy2;
            return box;
        }
    }

    static class ReentrantThreadLocal<K extends ReentrantContext>
        extends ThreadLocal<K>
    {
        final static int DEPTH_UNDEFINED = 0;
        final static int DEPTH_TL = 1;
        final static int DEPTH_CLQ = 2;

        // ReentrantContext queue for child contexts
        private final ConcurrentLinkedQueue<K> ctxQueue
            = new ConcurrentLinkedQueue<K>();

        /**
         * Give a ReentrantContext instance from thread-local or CLQ storage
         * @return ReentrantContext instance
         */
        @Override
        public final K get() {
            K ctx = super.get();
            // Check reentrance:
            if (ctx.depth == ReentrantThreadLocal.DEPTH_UNDEFINED) {
                ctx.depth = ReentrantThreadLocal.DEPTH_TL;
            } else {
                // get or create another ReentrantContext from queue:
                ctx = ctxQueue.poll();
                if (ctx == null) {
                    // create a new ReentrantContext if none is available
                    ctx = initialValue();
                    ctx.depth = ReentrantThreadLocal.DEPTH_CLQ;
                }
            }
            return ctx;
        }

        /**
         * Restore the given ReentrantContext instance for reuse
         * @param ctx ReentrantContext instance
         */
        public final void restore(final K ctx) {
            if (ctx.depth == ReentrantThreadLocal.DEPTH_TL) {
                ctx.depth = ReentrantThreadLocal.DEPTH_UNDEFINED;
            } else {
                ctxQueue.offer(ctx);
            }
        }
    }

    static class ReentrantContext {
        int depth = ReentrantThreadLocal.DEPTH_UNDEFINED;
    }
}
