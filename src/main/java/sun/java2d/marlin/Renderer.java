/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Vector;
import sun.awt.geom.PathConsumer2D;
//import sun.awt.util.ThreadGroupUtils;
import sun.misc.ThreadGroupUtils;
import sun.misc.Unsafe;

final class Renderer implements PathConsumer2D, MarlinConst {
    // unsafe reference
    final static Unsafe unsafe;
    // array offset
    final static int OFFSET;
    // size of int / float
    final static int SIZE;

    // Renderer reference queue
    private final static ReferenceQueue<Renderer> rdrQueue
        = new ReferenceQueue<Renderer>();
    // reference list
    private final static Vector<OffHeapReference> refList
        = new Vector<OffHeapReference>(32);

    static {
        unsafe = Unsafe.getUnsafe();
        OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET;
        SIZE = Unsafe.ARRAY_INT_INDEX_SCALE;

        // Mimics Java2D Disposer:
        AccessController.doPrivileged(
            (PrivilegedAction<Void>) () -> {
                /*
                 * The thread must be a member of a thread group
                 * which will not get GCed before VM exit.
                 * Make its parent the top-level thread group.
                 */
                final ThreadGroup rootTG
                    = ThreadGroupUtils.getRootThreadGroup();
                final Thread t = new Thread(rootTG, new OffHeapDisposer(),
                    "MarlinRenderer Disposer");
                t.setContextClassLoader(null);
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY);
                t.start();
                return null;
            }
        );
    }

    // subpixels expressed as log2
    public final static int SUBPIXEL_LG_POSITIONS_X
        = MarlinRenderingEngine.getSubPixel_Log2_X();
    public final static int SUBPIXEL_LG_POSITIONS_Y
        = MarlinRenderingEngine.getSubPixel_Log2_Y();
    // number of subpixels
    public final static int SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
    public final static int SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);
    // use float to make tosubpix methods faster (no int to float conversion)
    public final static float f_SUBPIXEL_POSITIONS_X
        = (float) SUBPIXEL_POSITIONS_X;
    public final static float f_SUBPIXEL_POSITIONS_Y
        = (float) SUBPIXEL_POSITIONS_Y;
    public final static int SUBPIXEL_MASK_X = SUBPIXEL_POSITIONS_X - 1;
    public final static int SUBPIXEL_MASK_Y = SUBPIXEL_POSITIONS_Y - 1;
    public final static int MAX_AA_ALPHA
        = SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_Y;
    // number of subpixels corresponding to a tile line
    private static final int SUBPIXEL_TILE
        = MarlinCache.TILE_SIZE << SUBPIXEL_LG_POSITIONS_Y;

    // 2048 (pixelSize) pixels (height) x 8 subpixels = 64K
    static final int INITIAL_BUCKET_ARRAY
        = INITIAL_PIXEL_DIM * SUBPIXEL_POSITIONS_Y;

    // initial edges (16 bytes) = 32K [ints/floats] = 128K
    static final int INITIAL_EDGES_CAPACITY = INITIAL_ARRAY_16K << 3;

    public static final int WIND_EVEN_ODD = 0;
    public static final int WIND_NON_ZERO = 1;

    // common to all types of input path segments.
    // OFFSET as bytes
    // float values:
    public static final int OFF_F_CURX  = 0;
    public static final int OFF_SLOPE   = OFF_F_CURX + SIZE;
    // integer values:
    public static final int OFF_NEXT    = OFF_SLOPE + SIZE;
    public static final int OFF_YMAX_OR = OFF_NEXT + SIZE;

    // size of one edge in bytes
    public static final int SIZEOF_EDGE_BYTES = OFF_YMAX_OR + SIZE;

    // curve break into lines
    // cubic bind length (dx or dy) = 20 to decrement step
    public static final float DEC_BND = 20f;
    // cubic bind length (dx or dy) = 8 to increment step
    public static final float INC_BND = 8f;
    // cubic countlg
    private static final int CUB_COUNT_LG = 3;
    // cubic count = 2^countlg
    private static final int CUB_COUNT = 1 << CUB_COUNT_LG;
    // cubic count^2 = 4^countlg
    private static final int CUB_COUNT_2 = 1 << (2 * CUB_COUNT_LG);
    // cubic count^3 = 8^countlg
    private static final int CUB_COUNT_3 = 1 << (3 * CUB_COUNT_LG);
    // cubic 1 / count
    private static final float CUB_INV_COUNT = 1f / CUB_COUNT;
    // cubic 1 / count^2 = 1 / 4^countlg
    private static final float CUB_INV_COUNT_2 = 1f / CUB_COUNT_2;
    // cubic 1 / count^3 = 1 / 8^countlg
    private static final float CUB_INV_COUNT_3 = 1f / CUB_COUNT_3;

    // quad break
    // quadratic bind length (dx or dy) = 32 ie 4 subpixel ?
    public static final float QUAD_DEC_BND = 32f;
    // quadratic countlg
    private static final int QUAD_COUNT_LG = 4;
    // quadratic count = 2^countlg
    private static final int QUAD_COUNT = 1 << QUAD_COUNT_LG;
    // quadratic 1 / count^2
    private static final float QUAD_INV_COUNT_SQ = 1f / (QUAD_COUNT * QUAD_COUNT);

//////////////////////////////////////////////////////////////////////////////
//  SCAN LINE
//////////////////////////////////////////////////////////////////////////////
    // crossings ie subpixel edge x coordinates
    private int[] crossings;
    // auxiliary storage for crossings (merge sort)
    private int[] aux_crossings;

    // indices into the segment pointer lists. They indicate the "active"
    // sublist in the segment lists (the portion of the list that contains
    // all the segments that cross the next scan line).
    private int edgeCount;
    private int[] edgePtrs;
    // auxiliary storage for edge pointers (merge sort)
    private int[] aux_edgePtrs;

    // max used for both edgePtrs and crossings (stats only)
    private int activeEdgeMaxUsed;

    // per-thread initial arrays (large enough to satisfy most usages) (1024)
    private final int[] crossings_initial = new int[INITIAL_SMALL_ARRAY]; // 4K
    // +1 to avoid recycling in Helpers.widenArray()
    private final int[] edgePtrs_initial  = new int[INITIAL_SMALL_ARRAY + 1]; // 4K
    // merge sort initial arrays (large enough to satisfy most usages) (1024)
    private final int[] aux_crossings_initial = new int[INITIAL_SMALL_ARRAY]; // 4K
    private final int[] aux_edgePtrs_initial  = new int[INITIAL_SMALL_ARRAY + 1]; // 4K

//////////////////////////////////////////////////////////////////////////////
//  EDGE LIST
//////////////////////////////////////////////////////////////////////////////
// TODO(maybe): very tempting to use fixed point here. A lot of opportunities
// for shifts and just removing certain operations altogether.
    private float edgeMinY = Float.POSITIVE_INFINITY;
    private float edgeMaxY = Float.NEGATIVE_INFINITY;
    private float edgeMinX = Float.POSITIVE_INFINITY;
    private float edgeMaxX = Float.NEGATIVE_INFINITY;

    // edges [floats|ints] stored in off-heap memory
    private final OffHeapEdgeArray edges
        = new OffHeapEdgeArray(INITIAL_EDGES_CAPACITY); // 128K

    private int[] edgeBuckets;
    private int[] edgeBucketCounts; // 2*newedges + (1 if pruning needed)

    // +1 to avoid recycling in Helpers.widenArray()
    private final int[] edgeBuckets_initial
        = new int[INITIAL_BUCKET_ARRAY + 1]; // 64K
    private final int[] edgeBucketCounts_initial
        = new int[INITIAL_BUCKET_ARRAY + 1]; // 64K

    // Flattens using adaptive forward differencing. This only carries out
    // one iteration of the AFD loop. All it does is update AFD variables (i.e.
    // X0, Y0, D*[X|Y], COUNT; not variables used for computing scanline crossings).
    private void quadBreakIntoLinesAndAdd(float x0, float y0,
            final Curve c,
            final float x2, final float y2)
    {
        int count = QUAD_COUNT; // 2^countlg where count_lg=4
        float icount2 = QUAD_INV_COUNT_SQ; // 1 / count^2
        float maxDD = Math.max(c.dbx * icount2, c.dby * icount2);

        final float _QUAD_DEC_BND = QUAD_DEC_BND;

        while (maxDD > _QUAD_DEC_BND) { // 32
            maxDD /= 4f;
            count <<= 1;
        }

        float icount = 1f / count;
        icount2 = icount * icount; // 1 / count^2

        final float ddx = c.dbx * icount2;
        final float ddy = c.dby * icount2;
        float dx = c.bx * icount2 + c.cx * icount;
        float dy = c.by * icount2 + c.cy * icount;

        float x1, y1;
        int nL = 0; // line count

        while (count-- > 1) {
            x1 = x0 + dx;
            dx += ddx;
            y1 = y0 + dy;
            dy += ddy;
            addLine(x0, y0, x1, y1);
            if (doStats) { nL++; }
            x0 = x1;
            y0 = y1;
        }
        addLine(x0, y0, x2, y2);

        if (doStats) {
            RendererContext.stats.stat_rdr_quadBreak.add(nL + 1);
        }
    }

    // x0, y0 and x3,y3 are the endpoints of the curve. We could compute these
    // using c.xat(0),c.yat(0) and c.xat(1),c.yat(1), but this might introduce
    // numerical errors, and our callers already have the exact values.
    // Another alternative would be to pass all the control points, and call
    // c.set here, but then too many numbers are passed around.
    private void curveBreakIntoLinesAndAdd(float x0, float y0,
                                           final Curve c,
                                           final float x3, final float y3)
    {
        final float icount = CUB_INV_COUNT;    // 1 / count
        final float icount2 = CUB_INV_COUNT_2; // 1 / count^2
        final float icount3 = CUB_INV_COUNT_3; // 1 / count^3

        int count = CUB_COUNT;

        // the dx and dy refer to forward differencing variables, not the last
        // coefficients of the "points" polynomial
        float dddx, dddy, ddx, ddy, dx, dy;
        dddx = 2f * c.dax * icount3;
        dddy = 2f * c.day * icount3;
        ddx = dddx + c.dbx * icount2;
        ddy = dddy + c.dby * icount2;
        dx = c.ax * icount3 + c.bx * icount2 + c.cx * icount;
        dy = c.ay * icount3 + c.by * icount2 + c.cy * icount;

        // we use x0, y0 to walk the line
        float x1 = x0, y1 = y0;
        int nL = 0; // line count

        final float _DEC_BND = DEC_BND;
        final float _INC_BND = INC_BND;

        while (count > 0) {
            while (Math.abs(ddx) > _DEC_BND || Math.abs(ddy) > _DEC_BND) {
                dddx /= 8f;
                dddy /= 8f;
                ddx = ddx/4f - dddx;
                ddy = ddy/4f - dddy;
                dx = (dx - ddx) / 2f;
                dy = (dy - ddy) / 2f;
                count <<= 1;
            }
            // TODO: check these operations as they seem different
            // between previous and next loops (8,4,2 ratios)
            // can only do this on even "count" values, because we must divide count by 2
            while (count % 2 == 0
                    && Math.abs(dx) <= _INC_BND
                    && Math.abs(dy) <= _INC_BND) {
                dx = 2f * dx + ddx;
                dy = 2f * dy + ddy;
                ddx = 4f * (ddx + dddx);
                ddy = 4f * (ddy + dddy);
                dddx *= 8f;
                dddy *= 8f;
                count >>= 1;
            }
            count--;
            if (count > 0) {
                x1 += dx;
                dx += ddx;
                ddx += dddx;
                y1 += dy;
                dy += ddy;
                ddy += dddy;
            } else {
                x1 = x3;
                y1 = y3;
            }
            addLine(x0, y0, x1, y1);
            if (doStats) { nL++; }
            x0 = x1;
            y0 = y1;
        }
        if (doStats) {
            RendererContext.stats.stat_rdr_curveBreak.add(nL);
        }
    }

    private void addLine(float x1, float y1, float x2, float y2) {
        if (doMonitors) {
            RendererContext.stats.mon_rdr_addLine.start();
        }
        int or = 1; // orientation of the line. 1 if y increases, 0 otherwise.
        if (y2 < y1) {
            or = 0;
            float tmp = y2;
            y2 = y1;
            y1 = tmp;
            tmp = x2;
            x2 = x1;
            x1 = tmp;
        }

        // copy members:
        final int _boundsMinY = boundsMinY;

        /* TODO: improve accuracy using correct float rounding to int
        ie use ceil(x - 0.5f) */

        // convert subpixel coordinates (float) into pixel positions (int)
        // upper integer (inclusive)
        final int firstCrossing = Math.max(FloatMath.ceil(y1), _boundsMinY);

        // note: use boundsMaxY (last Y exclusive) to compute correct coverage
        // upper integer (exclusive ?)
        final int lastCrossing  = Math.min(FloatMath.ceil(y2),  boundsMaxY);

        /* skip horizontal lines in pixel space and clip edges
           out of y range [boundsMinY; boundsMaxY] */
        if (firstCrossing >= lastCrossing) {
            if (doMonitors) {
                RendererContext.stats.mon_rdr_addLine.stop();
            }
            return;
        }
        // edge min/max X/Y are in subpixel space (inclusive)
        if (y1 < edgeMinY) {
            edgeMinY = y1;
        }
        if (y2 > edgeMaxY) {
            edgeMaxY = y2;
        }

        final float slope = (x2 - x1) / (y2 - y1);

        if (slope > 0f) { // <==> x1 < x2
            if (x1 < edgeMinX) {
                edgeMinX = x1;
            }
            if (x2 > edgeMaxX) {
                edgeMaxX = x2;
            }
        } else {
            if (x2 < edgeMinX) {
                edgeMinX = x2;
            }
            if (x1 > edgeMaxX) {
                edgeMaxX = x1;
            }
        }


        // copy constants:
        final int _SIZEOF_EDGE_BYTES = SIZEOF_EDGE_BYTES;
        // copy members:
        final int[] _edgeBuckets      = edgeBuckets;
        final int[] _edgeBucketCounts = edgeBucketCounts;

        final OffHeapEdgeArray _edges = edges;
        // get free pointer (ie length in bytes)
        final int edgePtr = _edges.used;

        if (_edges.length < edgePtr + _SIZEOF_EDGE_BYTES) {
            // double size:
            final int edgeNewSize = edgePtr << 1;
            if (doStats) {
                RendererContext.stats.stat_rdr_edges_resizes.add(edgeNewSize);
            }
            _edges.resize(edgeNewSize);
        }


        final Unsafe _unsafe = unsafe;
        final long    addr   = _edges.address + edgePtr;

        // float values:
        _unsafe.putFloat(addr,             x1 + (firstCrossing - y1) * slope);
        _unsafe.putFloat(addr + OFF_SLOPE, slope);


        // each bucket is a linked list. this method adds ptr to the
        // start of the "bucket"th linked list.
        final int bucketIdx = firstCrossing - _boundsMinY;

        // integer values:
        // pointer from bucket
        _unsafe.putInt(addr + OFF_NEXT,    _edgeBuckets[bucketIdx]);
        // last bit corresponds to the orientation
        _unsafe.putInt(addr + OFF_YMAX_OR, (lastCrossing << 1) | or);

        // Update buckets:
        // directly the edge struct "pointer"
        _edgeBuckets[bucketIdx]       = edgePtr;
        _edgeBucketCounts[bucketIdx] += 2; // 1 << 1
        // last bit means edge end
        _edgeBucketCounts[lastCrossing - _boundsMinY] |= 0x1;

        // update free pointer (ie length in bytes)
        _edges.used += _SIZEOF_EDGE_BYTES;

        if (doMonitors) {
            RendererContext.stats.mon_rdr_addLine.stop();
        }
    }

// END EDGE LIST
//////////////////////////////////////////////////////////////////////////////

    // Cache to store RLE-encoded coverage mask of the current primitive
    final MarlinCache cache;

    // Bounds of the drawing region, at subpixel precision.
    private int boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    // Current winding rule
    private int windingRule;

    // Current drawing position, i.e., final point of last segment
    private float x0, y0;

    // Position of most recent 'moveTo' command
    private float pix_sx0, pix_sy0;

    // per-thread renderer context
    final RendererContext rdrCtx;
    // dirty curve
    private final Curve curve;

    Renderer(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        this.curve = rdrCtx.curve;

        edgeBuckets = edgeBuckets_initial;
        edgeBucketCounts = edgeBucketCounts_initial;

        alphaLine  = alphaLine_initial;

        this.cache = rdrCtx.cache;

        // ScanLine:
        crossings     = crossings_initial;
        aux_crossings = aux_crossings_initial;
        edgePtrs      = edgePtrs_initial;
        aux_edgePtrs  = aux_edgePtrs_initial;

        edgeCount = 0;
        activeEdgeMaxUsed = 0;

        // Create the phantom reference to ensure freeing off-heap memory:
        refList.add(new OffHeapReference(this, edges));
    }

    Renderer init(final int pix_boundsX, final int pix_boundsY,
                  final int pix_boundsWidth, final int pix_boundsHeight,
                  final int windingRule) {

        this.windingRule = windingRule;

        // bounds as half-open intervals: minX <= x < maxX and minY <= y < maxY
        this.boundsMinX =  pix_boundsX << SUBPIXEL_LG_POSITIONS_X;
        this.boundsMaxX =
            (pix_boundsX + pix_boundsWidth) << SUBPIXEL_LG_POSITIONS_X;
        this.boundsMinY =  pix_boundsY << SUBPIXEL_LG_POSITIONS_Y;
        this.boundsMaxY =
            (pix_boundsY + pix_boundsHeight) << SUBPIXEL_LG_POSITIONS_Y;

        if (doLogBounds) {
            MarlinUtils.logInfo("boundsXY = [" + boundsMinX + " ... "
                                + boundsMaxX + "[ [" + boundsMinY + " ... "
                                + boundsMaxY + "[");
        }

        // see addLine: ceil(boundsMaxY) => boundsMaxY + 1
        // +1 for edgeBucketCounts
        final int edgeBucketsLength = (boundsMaxY - boundsMinY) + 1;

        if (edgeBucketsLength > INITIAL_BUCKET_ARRAY) {
            if (doStats) {
                rdrCtx.stats.stat_array_renderer_edgeBuckets
                    .add(edgeBucketsLength);
                rdrCtx.stats.stat_array_renderer_edgeBucketCounts
                    .add(edgeBucketsLength);
            }
            edgeBuckets = rdrCtx.getIntArray(edgeBucketsLength);
            edgeBucketCounts = rdrCtx.getIntArray(edgeBucketsLength);
        }

        edgeMinY = Float.POSITIVE_INFINITY;
        edgeMaxY = Float.NEGATIVE_INFINITY;
        edgeMinX = Float.POSITIVE_INFINITY;
        edgeMaxX = Float.NEGATIVE_INFINITY;

        // reset used mark:
        edges.used = 0;

        return this; // fluent API
    }

    /**
     * Disposes this renderer and recycle it clean up before reusing this instance
     */
    void dispose() {
        if (crossings != crossings_initial) {
            rdrCtx.putDirtyIntArray(crossings);
            crossings = crossings_initial;
            if (aux_crossings != aux_crossings_initial) {
                rdrCtx.putDirtyIntArray(aux_crossings);
                aux_crossings = aux_crossings_initial;
            }
        }
        if (edgePtrs != edgePtrs_initial) {
            rdrCtx.putDirtyIntArray(edgePtrs);
            edgePtrs = edgePtrs_initial;
            if (aux_edgePtrs != aux_edgePtrs_initial) {
                rdrCtx.putDirtyIntArray(aux_edgePtrs);
                aux_edgePtrs = aux_edgePtrs_initial;
            }
        }
        if (doCleanDirty) {
            // keep crossings and edgePtrs dirty
            Arrays.fill(crossings,     0);
            Arrays.fill(aux_crossings, 0);
            Arrays.fill(edgePtrs,      0);
            Arrays.fill(aux_edgePtrs,  0);
        }
        if (doStats) {
            RendererContext.stats.stat_rdr_activeEdges.add(activeEdgeMaxUsed);
        }
        edgeCount = 0;
        activeEdgeMaxUsed = 0;

        // Return arrays:
        if (doStats) {
            RendererContext.stats.stat_rdr_edges.add(edges.used);
            RendererContext.stats.stat_rdr_edges_count.add(edges.used / SIZEOF_EDGE_BYTES);
        }
        // resize back off-heap edges to initial size
        if (edges.length != INITIAL_EDGES_CAPACITY) {
            edges.resize(INITIAL_EDGES_CAPACITY);
        }
        if (doCleanDirty) {
            edges.fill(BYTE_0);
        }
        if (alphaLine != alphaLine_initial) {
            rdrCtx.putIntArray(alphaLine, 0, 0); // already zero filled
            alphaLine = alphaLine_initial;
        }

        if (edgeMinY != Float.POSITIVE_INFINITY) {
            final int _boundsMinY = boundsMinY;
            // Find used part
            // TODO: fix ceil:
            final int iminY =     Math.max(FloatMath.ceil(edgeMinY), _boundsMinY)
                                  - _boundsMinY;
            final int imaxY = 1 + Math.min(FloatMath.ceil(edgeMaxY),  boundsMaxY)
                                  - _boundsMinY;

            if (edgeBuckets == edgeBuckets_initial) {
                // fill only used part
                IntArrayCache.fill(edgeBuckets,      iminY, imaxY - 1, 0);
                IntArrayCache.fill(edgeBucketCounts, iminY, imaxY,     0);
            } else {
                 // clear only used part
                rdrCtx.putIntArray(edgeBuckets, iminY, imaxY - 1);
                edgeBuckets = edgeBuckets_initial;

                rdrCtx.putIntArray(edgeBucketCounts, iminY, imaxY);
                edgeBucketCounts = edgeBucketCounts_initial;
            }
        } else if (edgeBuckets != edgeBuckets_initial) {
            // unused arrays
            rdrCtx.putIntArray(edgeBuckets, 0, 0);
            edgeBuckets = edgeBuckets_initial;

            rdrCtx.putIntArray(edgeBucketCounts, 0, 0);
            edgeBucketCounts = edgeBucketCounts_initial;
        }
        if (doMonitors) {
            RendererContext.stats.mon_rdr_endRendering.stop();
            RendererContext.stats.mon_pre_getAATileGenerator.stop();
        }
        // recycle the RendererContext instance
        MarlinRenderingEngine.returnRendererContext(rdrCtx);
    }

    private static float tosubpixx(final float pix_x) {
        return f_SUBPIXEL_POSITIONS_X * pix_x;
    }

    private static float tosubpixy(final float pix_y) {
        return f_SUBPIXEL_POSITIONS_Y * pix_y;
    }

    @Override
    public void moveTo(float pix_x0, float pix_y0) {
        closePath();
        this.pix_sx0 = pix_x0;
        this.pix_sy0 = pix_y0;
        this.y0 = tosubpixy(pix_y0);
        this.x0 = tosubpixx(pix_x0);
    }

    @Override
    public void lineTo(float pix_x1, float pix_y1) {
        float x1 = tosubpixx(pix_x1);
        float y1 = tosubpixy(pix_y1);
        addLine(x0, y0, x1, y1);
        x0 = x1;
        y0 = y1;
    }

    @Override
    public void curveTo(float x1, float y1,
            float x2, float y2,
            float x3, float y3)
    {
        final float xe = tosubpixx(x3);
        final float ye = tosubpixy(y3);
        curve.set(x0, y0, tosubpixx(x1), tosubpixy(y1),
                          tosubpixx(x2), tosubpixy(y2), xe, ye);
        curveBreakIntoLinesAndAdd(x0, y0, curve, xe, ye);
        x0 = xe;
        y0 = ye;
    }

    @Override
    public void quadTo(float x1, float y1, float x2, float y2) {
        final float xe = tosubpixx(x2);
        final float ye = tosubpixy(y2);
        curve.set(x0, y0, tosubpixx(x1), tosubpixy(y1), xe, ye);
        quadBreakIntoLinesAndAdd(x0, y0, curve, xe, ye);
        x0 = xe;
        y0 = ye;
    }

    @Override
    public void closePath() {
        // lineTo expects its input in pixel coordinates.
        lineTo(pix_sx0, pix_sy0);
    }

    @Override
    public void pathDone() {
        closePath();
    }

    @Override
    public long getNativeConsumer() {
        throw new InternalError("Renderer does not use a native consumer.");
    }

    // clean alpha array (zero filled)
    private int[] alphaLine;
    // 2048 (pixelsize) pixel large
    private final int[] alphaLine_initial = new int[INITIAL_AA_ARRAY]; // 16K

    private void _endRendering(final int ymin, final int ymax) {

        // Get X bounds as true pixel boundaries to compute correct pixel coverage:
        final int bboxx0 = bbox_spminX;
        final int bboxx1 = bbox_spmaxX;

        // Mask to determine the relevant bit of the crossing sum
        // 0x1 if EVEN_ODD, all bits if NON_ZERO
        final boolean windingRuleEvenOdd = (windingRule == WIND_EVEN_ODD);

        // Useful when processing tile line by tile line
        final int[] _alpha = alphaLine;

        // local vars (performance):
        final MarlinCache _cache = cache;
        final OffHeapEdgeArray _edges = edges;
        final int[] _edgeBuckets = edgeBuckets;
        final int[] _edgeBucketCounts = edgeBucketCounts;

        int[] _crossings = this.crossings;
        int[] _edgePtrs  = this.edgePtrs;

        // merge sort auxiliary storage:
        int[] _aux_crossings = this.aux_crossings;
        int[] _aux_edgePtrs  = this.aux_edgePtrs;

        // copy constants:
        final int _OFF_SLOPE   = OFF_SLOPE;
        final int _OFF_NEXT    = OFF_NEXT;
        final int _OFF_YMAX_OR = OFF_YMAX_OR;

        // unsafe I/O:
        final Unsafe _unsafe = unsafe;
        final long    addr0  = _edges.address;
        long addr;
        final int _SUBPIXEL_LG_POSITIONS_X = SUBPIXEL_LG_POSITIONS_X;
        final int _SUBPIXEL_LG_POSITIONS_Y = SUBPIXEL_LG_POSITIONS_Y;
        final int _SUBPIXEL_MASK_X = SUBPIXEL_MASK_X;
        final int _SUBPIXEL_MASK_Y = SUBPIXEL_MASK_Y;
        final int _SUBPIXEL_POSITIONS_X = SUBPIXEL_POSITIONS_X;

        final int _MIN_VALUE = Integer.MIN_VALUE;
        final int _MAX_VALUE = Integer.MAX_VALUE;

        // Now we iterate through the scanlines. We must tell emitRow the coord
        // of the first non-transparent pixel, so we must keep accumulators for
        // the first and last pixels of the section of the current pixel row
        // that we will emit.
        // We also need to accumulate pix_bbox, but the iterator does it
        // for us. We will just get the values from it once this loop is done
        int pix_minX = _MAX_VALUE;
        int pix_maxX = _MIN_VALUE;

        int y = ymin;
        int bucket = y - boundsMinY;

        int numCrossings = this.edgeCount;
        int edgePtrsLen = _edgePtrs.length;
        int crossingsLen = _crossings.length;
        int _arrayMaxUsed = activeEdgeMaxUsed;
        int ptrLen = 0, newCount, ptrEnd;

        int bucketcount, i, j, ecur, lowx, highx;
        int cross, lastCross;
        float f_curx;
        int x0, x1, tmp, sum, prev, curx, curxo, crorientation;
        int pix_x, pix_xmaxm1, pix_xmax;

        int low, high, mid, prevNumCrossings;
        boolean useBinarySearch;

        int lastY = -1; // last emited row


        // Iteration on scanlines
        for (; y < ymax; y++, bucket++) {
            // --- from former ScanLineIterator.next()
            bucketcount = _edgeBucketCounts[bucket];

            // marker on previously sorted edges:
            prevNumCrossings = numCrossings;

            // bucketCount indicates new edge / edge end:
            if (bucketcount != 0) {
                if (doStats) {
                    RendererContext.stats.stat_rdr_activeEdges_updates
                        .add(numCrossings);
                }

                // last bit set to 1 means that edges ends
                if ((bucketcount & 0x1) != 0) {
                    /* note: edge[YMAX] is multiplied by 2
                       so compare it with 2*y + 1 (any orientation) */
                    final int yLim = (y << 1) | 0x1;
                    // eviction in active edge list
                    // cache edges[] address + offset
                    addr = addr0 + _OFF_YMAX_OR;

                    for (i = 0, newCount = 0; i < numCrossings; i++) {
                        // get the pointer to the edge
                        ecur = _edgePtrs[i];
                        // random access so use unsafe:
                        /* note: ymax is multiplied by 2
                           (1 bit shift to store orientation) */
                        if (_unsafe.getInt(addr + ecur) > yLim) {
                            _edgePtrs[newCount++] = ecur;
                        }
                    }
                    // update marker on sorted edges minus removed edges:
                    prevNumCrossings = numCrossings = newCount;
                }

                ptrLen = bucketcount >> 1; // number of new edge

                if (ptrLen != 0) {
                    if (doStats) {
                        RendererContext.stats.stat_rdr_activeEdges_adds
                            .add(ptrLen);
                        if (ptrLen > 10) {
                            RendererContext.stats.stat_rdr_activeEdges_adds_high
                                .add(ptrLen);
                        }
                    }
                    ptrEnd = numCrossings + ptrLen;

                    if (edgePtrsLen < ptrEnd) {
                        if (doStats) {
                            rdrCtx.stats.stat_array_renderer_edgePtrs
                                .add(ptrEnd);
                        }
                        this.edgePtrs = _edgePtrs
                            = rdrCtx.widenDirtyIntArray(_edgePtrs, numCrossings,
                                                        ptrLen);

                        edgePtrsLen = _edgePtrs.length;
                        // Get larger auxiliary storage:
                        if (_aux_edgePtrs != aux_edgePtrs_initial) {
                            rdrCtx.putDirtyIntArray(_aux_edgePtrs);
                        }
                        // use ArrayCache.getNewSize() to use the same growing
                        // factor than widenDirtyIntArray():
                        if (doStats) {
                            rdrCtx.stats.stat_array_renderer_aux_edgePtrs
                                .add(ptrEnd);
                        }
                        this.aux_edgePtrs = _aux_edgePtrs
                            = rdrCtx.getDirtyIntArray(ArrayCache.getNewSize(
                                                        numCrossings));
                    }

                    // cache edges[] address + offset
                    addr = addr0 + _OFF_NEXT;

                    // add new edges to active edge list:
                    for (ecur = _edgeBuckets[bucket];
                         numCrossings < ptrEnd; numCrossings++)
                    {
                        // store the pointer to the edge
                        _edgePtrs[numCrossings] = ecur;
                        // random access so use unsafe:
                        ecur = _unsafe.getInt(addr + ecur);
                    }

                    if (crossingsLen < numCrossings) {
                        // Get larger array:
                        if (_crossings != crossings_initial) {
                            rdrCtx.putDirtyIntArray(_crossings);
                        }
                        if (doStats) {
                            rdrCtx.stats.stat_array_renderer_crossings
                                .add(numCrossings);
                        }
                        this.crossings = _crossings
                            = rdrCtx.getDirtyIntArray(numCrossings);

                        // Get larger auxiliary storage:
                        if (_aux_crossings != aux_crossings_initial) {
                            rdrCtx.putDirtyIntArray(_aux_crossings);
                        }
                        if (doStats) {
                            rdrCtx.stats.stat_array_renderer_aux_crossings
                                .add(numCrossings);
                        }
                        this.aux_crossings = _aux_crossings
                            = rdrCtx.getDirtyIntArray(numCrossings);

                        crossingsLen = _crossings.length;
                    }
                    if (doStats) {
                        // update max used mark
                        if (numCrossings > _arrayMaxUsed) {
                            _arrayMaxUsed = numCrossings;
                        }
                    }
                } // ptrLen != 0
            } // bucketCount != 0


            if (numCrossings != 0) {
                /*
                 * thresholds to switch to optimized merge sort
                 * for newly added edges + final merge pass.
                 */
                if ((ptrLen < 10) || (numCrossings < 40)) {
                    if (doStats) {
                        RendererContext.stats.hist_rdr_crossings
                            .add(numCrossings);
                        RendererContext.stats.hist_rdr_crossings_adds
                            .add(ptrLen);
                    }

                    /*
                     * threshold to use binary insertion sort instead of
                     * straight insertion sort (to reduce minimize comparisons).
                     */
                    useBinarySearch = (numCrossings >= 20);

                    // if small enough:
                    lastCross = _MIN_VALUE;

                    for (i = 0; i < numCrossings; i++) {
                        // get the pointer to the edge
                        ecur = _edgePtrs[i];

                        // random access so use unsafe:
                        addr = addr0 + ecur; // ecur + OFF_F_CURX
                        f_curx = _unsafe.getFloat(addr);

                        /* convert subpixel coordinates (float) into pixel
                            positions (int) for coming scanline */
                        /* note: it is faster to always update edges even
                           if it is removed from AEL for coming or last scanline */
                        // random access so use unsafe:
                        _unsafe.putFloat(addr,
                                         f_curx + _unsafe.getFloat(addr +
                                                                   _OFF_SLOPE)); // ecur + _SLOPE

                        // update crossing ( x-coordinate + last bit = orientation (0 or 1)):
                        cross = (((int) f_curx) << 1)
                                | _unsafe.getInt(addr + _OFF_YMAX_OR) & 0x1;

                        if (doStats) {
                            RendererContext.stats.stat_rdr_crossings_updates
                                .add(numCrossings);
                        }

                        // insertion sort of crossings:
                        if (cross < lastCross) {
                            if (doStats) {
                                RendererContext.stats.stat_rdr_crossings_sorts
                                    .add(i);
                            }

                            /* use binary search for newly added edges
                               in crossings if arrays are large enough */
                            if (useBinarySearch && (i >= prevNumCrossings)) {
                                if (doStats) {
                                    RendererContext.stats.
                                        stat_rdr_crossings_bsearch.add(i);
                                }
                                low = 0;
                                high = i - 1;

                                do {
                                    mid = (low + high) >> 1;

                                    if (_crossings[mid] < cross) {
                                        low = mid + 1;
                                    } else {
                                        high = mid - 1;
                                    }
                                } while (low <= high);

                                for (j = i - 1; j >= low; j--) {
                                    _crossings[j + 1] = _crossings[j];
                                    _edgePtrs [j + 1] = _edgePtrs[j];
                                }
                                _crossings[low] = cross;
                                _edgePtrs [low] = ecur;

                            } else {
                                j = i - 1;
                                _crossings[i] = _crossings[j];
                                _edgePtrs[i] = _edgePtrs[j];

                                while ((--j >= 0) && (_crossings[j] > cross)) {
                                    _crossings[j + 1] = _crossings[j];
                                    _edgePtrs [j + 1] = _edgePtrs[j];
                                }
                                _crossings[j + 1] = cross;
                                _edgePtrs [j + 1] = ecur;
                            }

                        } else {
                            _crossings[i] = lastCross = cross;
                        }
                    }
                } else {
                    if (doStats) {
                        RendererContext.stats.stat_rdr_crossings_msorts
                            .add(numCrossings);
                        RendererContext.stats.hist_rdr_crossings_ratio
                            .add((1000 * ptrLen) / numCrossings);
                        RendererContext.stats.hist_rdr_crossings_msorts
                            .add(numCrossings);
                        RendererContext.stats.hist_rdr_crossings_msorts_adds
                            .add(ptrLen);
                    }

                    // Copy sorted data in auxiliary arrays
                    // and perform insertion sort on almost sorted data
                    // (ie i < prevNumCrossings):

                    lastCross = _MIN_VALUE;

                    for (i = 0; i < numCrossings; i++) {
                        // get the pointer to the edge
                        ecur = _edgePtrs[i];

                        // random access so use unsafe:
                        addr = addr0 + ecur; // ecur + OFF_F_CURX
                        f_curx = _unsafe.getFloat(addr);

                        /* convert subpixel coordinates (float) into pixel
                           positions (int) for coming scanline */
                        /* note: it is faster to always update edges even
                           if it is removed from AEL for coming or last scanline */
                        // random access so use unsafe:
                        _unsafe.putFloat(addr,
                                         f_curx + _unsafe.getFloat(addr +
                                                                   _OFF_SLOPE)); // ecur + _SLOPE

                        // update crossing ( x-coordinate + last bit = orientation (0 or 1)):
                        cross = (((int) f_curx) << 1)
                                | _unsafe.getInt(addr + _OFF_YMAX_OR) & 0x1;

                        if (doStats) {
                            RendererContext.stats.stat_rdr_crossings_updates
                                .add(numCrossings);
                        }

                        if (i >= prevNumCrossings) {
                            // simply store crossing as edgePtrs is in-place:
                            // will be copied and sorted efficiently by mergesort later:
                            _crossings[i]     = cross;

                        } else if (cross < lastCross) {
                            if (doStats) {
                                RendererContext.stats.stat_rdr_crossings_sorts
                                    .add(i);
                            }

                            // (straight) insertion sort of crossings:
                            j = i - 1;
                            _aux_crossings[i] = _aux_crossings[j];
                            _aux_edgePtrs[i] = _aux_edgePtrs[j];

                            while ((--j >= 0) && (_aux_crossings[j] > cross)) {
                                _aux_crossings[j + 1] = _aux_crossings[j];
                                _aux_edgePtrs [j + 1] = _aux_edgePtrs[j];
                            }
                            _aux_crossings[j + 1] = cross;
                            _aux_edgePtrs [j + 1] = ecur;

                        } else {
                            // auxiliary storage:
                            _aux_crossings[i] = lastCross = cross;
                            _aux_edgePtrs [i] = ecur;
                        }
                    }

                    // use Mergesort using auxiliary arrays (sort only right part)
                    MergeSort.mergeSortNoCopy(_crossings,     _edgePtrs,
                                              _aux_crossings, _aux_edgePtrs,
                                              numCrossings,   prevNumCrossings);
                }

                // reset ptrLen
                ptrLen = 0;
                // --- from former ScanLineIterator.next()

                // right shift on crossings to get the x-coordinate:
                lowx = _crossings[0] >> 1;
                highx = _crossings[numCrossings - 1] >> 1;

                /* note: bboxx0 and bboxx1 must be pixel boundaries
                   to have correct coverage computation */
                x0 = (lowx  > bboxx0) ?  lowx : bboxx0;
                x1 = (highx < bboxx1) ? highx : bboxx1;

                tmp = x0 >> _SUBPIXEL_LG_POSITIONS_X;
                if (tmp < pix_minX) {
                    pix_minX = tmp;
                }
                tmp = x1 >> _SUBPIXEL_LG_POSITIONS_X;
                if (tmp > pix_maxX) {
                    pix_maxX = tmp;
                }

                // compute pixel coverages
                curxo = _crossings[0];
                prev = curx = curxo >> 1;
                // to turn {0, 1} into {-1, 1}, multiply by 2 and subtract 1.
                // last bit contains orientation (0 or 1)
                crorientation = ((curxo & 0x1) << 1) - 1;

                if (windingRuleEvenOdd) {
                    sum = crorientation;

                    // Even Odd winding rule: take care of mask ie sum(orientations)
                    for (i = 1; i < numCrossings; i++) {
                        curxo = _crossings[i];
                        curx  =  curxo >> 1;
                        // to turn {0, 1} into {-1, 1}, multiply by 2 and subtract 1.
                        // last bit contains orientation (0 or 1)
                        crorientation = ((curxo & 0x1) << 1) - 1;

                        if ((sum & 0x1) != 0) {
                            x0 = (prev > bboxx0) ? prev : bboxx0;
                            x1 = (curx < bboxx1) ? curx : bboxx1;

                            if (x0 < x1) {
                                x0 -= bboxx0; // turn x0, x1 from coords to indices
                                x1 -= bboxx0; // in the alpha array.

                                pix_x      =  x0      >> _SUBPIXEL_LG_POSITIONS_X;
                                pix_xmaxm1 = (x1 - 1) >> _SUBPIXEL_LG_POSITIONS_X;

                                if (pix_x == pix_xmaxm1) {
                                    // Start and end in same pixel
                                    tmp = (x1 - x0); // number of subpixels
                                    _alpha[pix_x    ] += tmp;
                                    _alpha[pix_x + 1] -= tmp;
                                } else {
                                    tmp = (x0 & _SUBPIXEL_MASK_X);
                                    _alpha[pix_x    ]
                                        += (_SUBPIXEL_POSITIONS_X - tmp);
                                    _alpha[pix_x + 1]
                                        += tmp;

                                    pix_xmax = x1 >> _SUBPIXEL_LG_POSITIONS_X;

                                    tmp = (x1 & _SUBPIXEL_MASK_X);
                                    _alpha[pix_xmax    ]
                                        -= (_SUBPIXEL_POSITIONS_X - tmp);
                                    _alpha[pix_xmax + 1]
                                        -= tmp;
                                }
                            }
                        }

                        sum += crorientation;
                        prev = curx;
                    }
                } else {
                    // Non-zero winding rule: optimize that case (default)
                    // and avoid processing intermediate crossings
                    for (i = 1, sum = 0;; i++) {
                        sum += crorientation;

                        if (sum != 0) {
                            // prev = min(curx)
                            if (prev > curx) {
                                prev = curx;
                            }
                        } else {
                            x0 = (prev > bboxx0) ? prev : bboxx0;
                            x1 = (curx < bboxx1) ? curx : bboxx1;

                            if (x0 < x1) {
                                x0 -= bboxx0; // turn x0, x1 from coords to indices
                                x1 -= bboxx0; // in the alpha array.

                                pix_x      =  x0      >> _SUBPIXEL_LG_POSITIONS_X;
                                pix_xmaxm1 = (x1 - 1) >> _SUBPIXEL_LG_POSITIONS_X;

                                if (pix_x == pix_xmaxm1) {
                                    // Start and end in same pixel
                                    tmp = (x1 - x0); // number of subpixels
                                    _alpha[pix_x    ] += tmp;
                                    _alpha[pix_x + 1] -= tmp;
                                } else {
                                    tmp = (x0 & _SUBPIXEL_MASK_X);
                                    _alpha[pix_x    ]
                                        += (_SUBPIXEL_POSITIONS_X - tmp);
                                    _alpha[pix_x + 1]
                                        += tmp;

                                    pix_xmax = x1 >> _SUBPIXEL_LG_POSITIONS_X;

                                    tmp = (x1 & _SUBPIXEL_MASK_X);
                                    _alpha[pix_xmax    ]
                                        -= (_SUBPIXEL_POSITIONS_X - tmp);
                                    _alpha[pix_xmax + 1]
                                        -= tmp;
                                }
                            }
                            prev = _MAX_VALUE;
                        }

                        if (i == numCrossings) {
                            break;
                        }

                        curxo = _crossings[i];
                        curx  =  curxo >> 1;
                        // to turn {0, 1} into {-1, 1}, multiply by 2 and subtract 1.
                        // last bit contains orientation (0 or 1)
                        crorientation = ((curxo & 0x1) << 1) - 1;
                    }
                }
            } // numCrossings > 0

            // even if this last row had no crossings, alpha will be zeroed
            // from the last emitRow call. But this doesn't matter because
            // maxX < minX, so no row will be emitted to the MarlinCache.
            if ((y & _SUBPIXEL_MASK_Y) == _SUBPIXEL_MASK_Y) {
                lastY = y >> _SUBPIXEL_LG_POSITIONS_Y;
                if (pix_maxX >= pix_minX) {
                    // note: alpha array will be zeroed by copyAARow()
                    // +2 because alpha [pix_minX; pix_maxX+1]
                    // fix range [x0; x1[
                    _cache.copyAARow(_alpha, lastY, pix_minX, pix_maxX + 2);
                } else {
                    _cache.clearAARow(lastY);
                }
                pix_minX = _MAX_VALUE;
                pix_maxX = _MIN_VALUE;
            }
        } // scan line iterator

        // Emit final row
        y--;
        y >>= _SUBPIXEL_LG_POSITIONS_Y;

        if (pix_maxX >= pix_minX) {
            // note: alpha array will be zeroed by copyAARow()
            // +2 because alpha [pix_minX; pix_maxX+1]
            // fix range [x0; x1[
            _cache.copyAARow(_alpha, y, pix_minX, pix_maxX + 2);
        } else if (y != lastY) {
            _cache.clearAARow(y);
        }

        // update member:
        edgeCount = numCrossings;

        if (doStats) {
            // update max used mark
            activeEdgeMaxUsed = _arrayMaxUsed;
        }
    }

    boolean endRendering() {
        if (edgeMinY == Float.POSITIVE_INFINITY) {
            return false; // undefined edges bounds
        }

        /* TODO: improve accuracy using correct float rounding to int
           ie use ceil(x - 0.5f) */

        // bounds as inclusive intervals
        // TODO: fix -1 in boundsMaxX and boundsMaxY
        final int spminX = Math.max(FloatMath.ceil(edgeMinX), boundsMinX);
        final int spmaxX = Math.min(FloatMath.ceil(edgeMaxX), boundsMaxX - 1);
        final int spminY = Math.max(FloatMath.ceil(edgeMinY), boundsMinY);
        final int spmaxY = Math.min(FloatMath.ceil(edgeMaxY), boundsMaxY - 1);

        if (doLogBounds) {
            MarlinUtils.logInfo("edgesXY = [" + edgeMinX + " ... " + edgeMaxX
                                + "][" + edgeMinY + " ... " + edgeMaxY + "]");
            MarlinUtils.logInfo("spXY    = [" + spminX + " ... " + spmaxX
                                + "][" + spminY + " ... " + spmaxY + "]");
        }

        // test clipping for shapes out of bounds
        if ((spminX > spmaxX) || (spminY > spmaxY)) {
            return false;
        }

        // half open intervals
        // inclusive:
        final int pminX =  spminX                    >> SUBPIXEL_LG_POSITIONS_X;
        // exclusive:
        final int pmaxX = (spmaxX + SUBPIXEL_MASK_X) >> SUBPIXEL_LG_POSITIONS_X;
        // inclusive:
        final int pminY =  spminY                    >> SUBPIXEL_LG_POSITIONS_Y;
        // exclusive:
        final int pmaxY = (spmaxY + SUBPIXEL_MASK_Y) >> SUBPIXEL_LG_POSITIONS_Y;

        // store BBox to answer ptg.getBBox():
        this.cache.init(pminX, pminY, pmaxX, pmaxY);

        // memorize the rendering bounding box:
        /* note: bbox_spminX and bbox_spmaxX must be pixel boundaries
           to have correct coverage computation */
        // inclusive:
        bbox_spminX = pminX << SUBPIXEL_LG_POSITIONS_X;
        // exclusive:
        bbox_spmaxX = pmaxX << SUBPIXEL_LG_POSITIONS_X;
        // inclusive:
        bbox_spminY = spminY;
        // exclusive:
        bbox_spmaxY = Math.min(spmaxY + 1, pmaxY << SUBPIXEL_LG_POSITIONS_Y);

        if (doLogBounds) {
            MarlinUtils.logInfo("pXY       = [" + pminX + " ... " + pmaxX
                                + "[ [" + pminY + " ... " + pmaxY + "[");
            MarlinUtils.logInfo("bbox_spXY = [" + bbox_spminX + " ... "
                                + bbox_spmaxX + "[ [" + bbox_spminY + " ... "
                                + bbox_spmaxY + "[");
        }

        // Prepare alpha line:
        // add 2 to better deal with the last pixel in a pixel row.
        final int width = (pmaxX - pminX) + 2;

        // Useful when processing tile line by tile line
        if (width > INITIAL_AA_ARRAY) {
            if (doStats) {
                rdrCtx.stats.stat_array_renderer_alphaline
                    .add(width);
            }
            alphaLine = rdrCtx.getIntArray(width);
        }

        if (doMonitors) {
            RendererContext.stats.mon_rdr_endRendering.start();
        }

        // process first tile line:
        endRendering(pminY);

        return true;
    }

    private int bbox_spminX, bbox_spmaxX, bbox_spminY, bbox_spmaxY;

    void endRendering(final int pminY) {
        final int spminY       = pminY << SUBPIXEL_LG_POSITIONS_Y;
        final int fixed_spminY = Math.max(bbox_spminY, spminY);

        // avoid rendering for last call to nextTile()
        if (fixed_spminY < bbox_spmaxY) {
            // process a complete tile line ie scanlines for 32 rows
            final int spmaxY = Math.min(bbox_spmaxY, spminY + SUBPIXEL_TILE);

            // process tile line [0 - 32]
            cache.resetTileLine(pminY);

            if (doMonitors) {
                RendererContext.stats.mon_rdr_endRendering_Y.start();
            }

            // Process only one tile line:
            _endRendering(fixed_spminY, spmaxY);

            if (doMonitors) {
                RendererContext.stats.mon_rdr_endRendering_Y.stop();
            }
        }
    }

    static final class OffHeapEdgeArray  {
        long address;
        long length;
        int  used;

        OffHeapEdgeArray(final long len) {
            this.address = unsafe.allocateMemory(len);
            this.length  = len;
            this.used    = 0;
            if (logUnsafeMalloc) {
                MarlinUtils.logInfo(System.currentTimeMillis()
                                    + ": OffHeapEdgeArray.allocateMemory = "
                                    + len + " to addr = " + this.address);
            }
        }

        /*
         * As realloc may change the address, updating address is MANDATORY
         * @param len new array length
         */
        void resize(final long len) {
            // TODO: handle OOME ?
            this.address = unsafe.reallocateMemory(address, len);
            this.length  = len;
            if (logUnsafeMalloc) {
                MarlinUtils.logInfo(System.currentTimeMillis()
                                    + ": OffHeapEdgeArray.reallocateMemory = "
                                    + len + " to addr = " + this.address);
            }
        }

        void free() {
            unsafe.freeMemory(this.address);
            if (logUnsafeMalloc) {
                MarlinUtils.logInfo(System.currentTimeMillis()
                                    + ": OffHeapEdgeArray.free = "
                                    + this.length
                                    + " at addr = " + this.address);
            }
        }

        void fill(final byte val) {
            unsafe.setMemory(this.address, this.length, val);
        }
    }

    static final class OffHeapReference extends PhantomReference<Renderer> {

        private final OffHeapEdgeArray edges;

        OffHeapReference(final Renderer rdr, final OffHeapEdgeArray edges) {
            super(rdr, rdrQueue);
            this.edges = edges;
        }

        void dispose() {
            // free off-heap blocks
            this.edges.free();
        }
    }

    static final class OffHeapDisposer implements Runnable {
        @Override
        public void run() {
            final Thread currentThread = Thread.currentThread();
            OffHeapReference ref;

            // check interrupted:
            for (; !currentThread.isInterrupted();) {
                try {
                    ref = (OffHeapReference)rdrQueue.remove();
                    ref.dispose();

                    refList.remove(ref);

                } catch (InterruptedException ie) {
                    MarlinUtils.logException("OffHeapDisposer interrupted:",
                                             ie);
                }
            }
        }
    }
}
