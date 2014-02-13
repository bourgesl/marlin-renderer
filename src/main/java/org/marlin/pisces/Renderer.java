/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import sun.awt.geom.PathConsumer2D;
import sun.misc.Unsafe;

final class Renderer implements PathConsumer2D, PiscesConst {

    private final static int OFFSET;
    private final static int SIZE;
    
    final static Unsafe unsafe;

    static {
        Unsafe ref = null;
        try {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            ref = (Unsafe) field.get(null);
        } catch (Exception e) {
            System.out.println("Unable to get Unsafe.");
            System.exit(1);
        }
        unsafe = ref;

        OFFSET = unsafe.ARRAY_INT_BASE_OFFSET;
//        FLOAT_OFFSET = unsafe.ARRAY_FLOAT_BASE_OFFSET;
        SIZE = unsafe.ARRAY_INT_INDEX_SCALE;
//        FLOAT_SIZE = unsafe.ARRAY_FLOAT_INDEX_SCALE;

        System.out.println("Using sun.misc.Unsafe: No warranty (may crash your JVM):\n" 
                           + "USE IT AT YOUR OWN RISKS = do not use in production)");

        System.out.println("INT_OFFSET = " + OFFSET);
        System.out.println("INT_SIZE = " + SIZE);
    }
    

    /* constants */
    /* hard coded 8x8 antialiasing -> 64 subpixels */
    /* may use System properties to define expected subpixels (4x4, 8x8, 16x16) */
    public final static int SUBPIXEL_LG_POSITIONS_X = 3;
    public final static int SUBPIXEL_LG_POSITIONS_Y = 3;
    public final static int SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
    public final static int SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);
    // LBO: use float to make tosubpix methods faster (no int to float conversion)
    public final static float f_SUBPIXEL_POSITIONS_X = (float) SUBPIXEL_POSITIONS_X;
    public final static float f_SUBPIXEL_POSITIONS_Y = (float) SUBPIXEL_POSITIONS_Y;
    public final static int SUBPIXEL_MASK_X = SUBPIXEL_POSITIONS_X - 1;
    public final static int SUBPIXEL_MASK_Y = SUBPIXEL_POSITIONS_Y - 1;
    public final static int MAX_AA_ALPHA = SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_Y;
    /* number of subpixels corresponding to a tile line */
    private static final int SUBPIXEL_TILE = PiscesCache.TILE_SIZE << SUBPIXEL_LG_POSITIONS_Y;

    /* 2048 pixels (height) x 8 subpixels = 64K */
    static final int INITIAL_BUCKET_ARRAY = INITIAL_PIXEL_DIM * SUBPIXEL_POSITIONS_Y;

    public static final int WIND_EVEN_ODD = 0;
    public static final int WIND_NON_ZERO = 1;

    // common to all types of input path segments.
    // float values:
    public static final int F_CURX = 0;
    public static final int SLOPE  = 1;
    // integer values:
/*    
    public static final int NEXT   = 0;
    public static final int YMAX   = 1;
    public static final int OR     = 2;
*/    
    public static final int NEXT   = 2;
    public static final int YMAX   = 3;
    public static final int OR     = 4;
    
    /** size of each edge in both arrays (5) */
    static final int SIZEOF_EDGE = OR + 1;

    /* curve break into lines */
    public static final float DEC_BND = 20f;
    public static final float INC_BND = 8f;

//////////////////////////////////////////////////////////////////////////////
//  SCAN LINE
//////////////////////////////////////////////////////////////////////////////
    /**
     * crossings ie subpixel edge x coordinates
     */
    int[] crossings;

    // indices into the segment pointer lists. They indicate the "active"
    // sublist in the segment lists (the portion of the list that contains
    // all the segments that cross the next scan line).
    private int edgeCount;
    private int[] edgePtrs;

    // LBO: max used for both edgePtrs and crossings
    private int activeEdgeMaxUsed;

    /* per-thread initial arrays (large enough to satisfy most usages (4096) */
    private final int[] crossings_initial = new int[INITIAL_MEDIUM_ARRAY]; // 16K
    // +1 to avoid recycling in Helpers.widenArray()
    private final int[] edgePtrs_initial  = new int[INITIAL_MEDIUM_ARRAY + 1];  // 16K

//////////////////////////////////////////////////////////////////////////////
//  EDGE LIST
//////////////////////////////////////////////////////////////////////////////
// TODO(maybe): very tempting to use fixed point here. A lot of opportunities
// for shifts and just removing certain operations altogether.
    private float edgeMinY = Float.POSITIVE_INFINITY;
    private float edgeMaxY = Float.NEGATIVE_INFINITY;
    private float edgeMinX = Float.POSITIVE_INFINITY;
    private float edgeMaxX = Float.NEGATIVE_INFINITY;

    /** current position in edge arrays (last used mark) */
    private int edgesPos;
    
    /** edges (dirty) */
// Try using a single array    
//    private float[] edges;

    /** edges (integer values) */
    private int[] edgesInt;

    /* LBO: very large initial edges array = 128K */
    // +1 to avoid recycling in Helpers.widenArray()
//    private final float[] edges_initial  = new float[INITIAL_ARRAY_32K + 1]; // 128K
    private final int[] edgesInt_initial = new int  [INITIAL_ARRAY_32K + 1]; // 128K

    private int[] edgeBuckets;
    private int[] edgeBucketCounts; // 2*newedges + (1 if pruning needed)

    private final int[] edgeBuckets_initial      = new int[INITIAL_BUCKET_ARRAY]; // 64K
    private final int[] edgeBucketCounts_initial = new int[INITIAL_BUCKET_ARRAY]; // 64K

    // Flattens using adaptive forward differencing. This only carries out
    // one iteration of the AFD loop. All it does is update AFD variables (i.e.
    // X0, Y0, D*[X|Y], COUNT; not variables used for computing scanline crossings).
    private void quadBreakIntoLinesAndAdd(float x0, float y0,
            final Curve c,
            final float x2, final float y2) {
        final float QUAD_DEC_BND = 32;
        final int countlg = 4;
        int count = 1 << countlg;
        int countsq = count * count;
        float maxDD = Math.max(c.dbx / countsq, c.dby / countsq);
        while (maxDD > QUAD_DEC_BND) {
            maxDD /= 4;
            count <<= 1;
        }

        countsq = count * count;
        final float ddx = c.dbx / countsq;
        final float ddy = c.dby / countsq;
        float dx = c.bx / countsq + c.cx / count;
        float dy = c.by / countsq + c.cy / count;

        float x1, y1;

        while (count-- > 1) {
            x1 = x0 + dx;
            dx += ddx;
            y1 = y0 + dy;
            dy += ddy;
            addLine(x0, y0, x1, y1);
            x0 = x1;
            y0 = y1;
        }
        addLine(x0, y0, x2, y2);
    }

    // x0, y0 and x3,y3 are the endpoints of the curve. We could compute these
    // using c.xat(0),c.yat(0) and c.xat(1),c.yat(1), but this might introduce
    // numerical errors, and our callers already have the exact values.
    // Another alternative would be to pass all the control points, and call c.set
    // here, but then too many numbers are passed around.
    private void curveBreakIntoLinesAndAdd(float x0, float y0,
            final Curve c,
            final float x3, final float y3) {
        final int countlg = 3;
        int count = 1 << countlg;

        // the dx and dy refer to forward differencing variables, not the last
        // coefficients of the "points" polynomial
        float dddx, dddy, ddx, ddy, dx, dy;
        dddx = 2f * c.dax / (1 << (3 * countlg));
        dddy = 2f * c.day / (1 << (3 * countlg));

        ddx = dddx + c.dbx / (1 << (2 * countlg));
        ddy = dddy + c.dby / (1 << (2 * countlg));
        dx = c.ax / (1 << (3 * countlg)) + c.bx / (1 << (2 * countlg)) + c.cx / (1 << countlg);
        dy = c.ay / (1 << (3 * countlg)) + c.by / (1 << (2 * countlg)) + c.cy / (1 << countlg);

        // we use x0, y0 to walk the line
        float x1 = x0, y1 = y0;
        while (count > 0) {
            while (Math.abs(ddx) > DEC_BND || Math.abs(ddy) > DEC_BND) {
                dddx /= 8;
                dddy /= 8;
                ddx = ddx / 4 - dddx;
                ddy = ddy / 4 - dddy;
                dx = (dx - ddx) / 2;
                dy = (dy - ddy) / 2;
                count <<= 1;
            }
            // can only do this on even "count" values, because we must divide count by 2
            while (count % 2 == 0 && Math.abs(dx) <= INC_BND && Math.abs(dy) <= INC_BND) {
                dx = 2 * dx + ddx;
                dy = 2 * dy + ddy;
                ddx = 4 * (ddx + dddx);
                ddy = 4 * (ddy + dddy);
                dddx = 8 * dddx;
                dddy = 8 * dddy;
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
            x0 = x1;
            y0 = y1;
        }
    }

    private void addLine(float x1, float y1, float x2, float y2) {
        if (doMonitors) {
            rdrCtx.mon_rdr_addLine.start();
        }
        int or = 1; // orientation of the line. 1 if y increases, -1 otherwise.
        if (y2 < y1) {
            or = -1;
            float tmp = y2;
            y2 = y1;
            y1 = tmp;
            tmp = x2;
            x2 = x1;
            x1 = tmp;
        }

        // copy members:
        final int _boundsMinY = boundsMinY;

        /* TODO: improve accuracy using correct float rounding to int ie use ceil(x - 0.5f) */
        
        /* convert subpixel coordinates (float) into pixel positions (int) */
        final int firstCrossing = Math.max(FastMath.ceil(y1), _boundsMinY); // upper integer (inclusive)
        /* note: use boundsMaxY (last Y exclusive) to compute correct coverage */
        final int lastCrossing  = Math.min(FastMath.ceil(y2),  boundsMaxY); // upper integer (exclusive ?)

        /* skip horizontal lines in pixel space */
        if (firstCrossing >= lastCrossing) {
            if (doMonitors) {
                rdrCtx.mon_rdr_addLine.stop();
            }
            return;
        }
        /* edge min/max X/Y are in subpixel space (inclusive) */
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

        final int ptr = edgesPos;

        // copy constants:
        final int _SIZEOF_EDGE = SIZEOF_EDGE;
        // copy members:
        final int[] _edgeBuckets      = edgeBuckets;
        final int[] _edgeBucketCounts = edgeBucketCounts;
        
  //      final float[] _edges;
        final int[]   _edgesInt;
        
//        if (edges.length < ptr + _SIZEOF_EDGE) {
        if (edgesInt.length < ptr + _SIZEOF_EDGE) {
//            edges    = _edges    = Helpers.widenArray(rdrCtx, edges,    ptr, _SIZEOF_EDGE, ptr);
            edgesInt = _edgesInt = Helpers.widenArray(rdrCtx, edgesInt, ptr, _SIZEOF_EDGE, ptr);
        } else {
//            _edges    = edges;
            _edgesInt = edgesInt;
        }

        // float values:
//        _edges[ptr /* + F_CURX */] = x1 + (firstCrossing - y1) * slope;
//        _edges[ptr + SLOPE]        = slope;
// 2=float as ints
//        _edgesInt[ptr /* + F_CURX */] = Float.floatToRawIntBits(x1 + (firstCrossing - y1) * slope);
//        _edgesInt[ptr + SLOPE]        = Float.floatToRawIntBits(slope);
// 3=unsafe
//        setFloat(_edgesInt, ptr,         x1 + (firstCrossing - y1) * slope);
//        setFloat(_edgesInt, ptr + SLOPE, slope);
// 4=direct
        final Unsafe _unsafe = unsafe;
        final long addr = OFFSET + (ptr << 2); // 4 = FLOAT_SIZE
        _unsafe.putFloat(_edgesInt, addr,     x1 + (firstCrossing - y1) * slope);
        _unsafe.putFloat(_edgesInt, addr + 4, slope); // +4 = +1 * FLOAT_SIZE

        // integer values:
        _edgesInt[ptr + YMAX] = lastCrossing;
        _edgesInt[ptr + OR]   = or;


        // each bucket is a linked list. this method adds ptr to the
        // start of the "bucket"th linked list.
        final int bucketIdx = firstCrossing - _boundsMinY; 
        
//        _edgesInt[ptr /* + NEXT */]   = _edgeBuckets[bucketIdx];
        _edgesInt[ptr + NEXT ]        = _edgeBuckets[bucketIdx];
        
        // Update buckets:
        _edgeBuckets[bucketIdx]       = ptr;
        _edgeBucketCounts[bucketIdx] += 2;
        _edgeBucketCounts[lastCrossing - _boundsMinY] |= 1; /* last bit means edge end */        

        edgesPos += _SIZEOF_EDGE;

        if (doMonitors) {
            rdrCtx.mon_rdr_addLine.stop();
        }
    }

// END EDGE LIST
//////////////////////////////////////////////////////////////////////////////

    /* Renderer members */
    // Cache to store RLE-encoded coverage mask of the current primitive
    final PiscesCache cache;

    // Bounds of the drawing region, at subpixel precision.
    private int boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    // Current winding rule
    private int windingRule;

    // Current drawing position, i.e., final point of last segment
    private float x0, y0;

    // Position of most recent 'moveTo' command
    private float pix_sx0, pix_sy0;

    /** per-thread renderer context */
    final RendererContext rdrCtx;
    /** dirty curve */
    private final Curve curve;

    Renderer(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        this.curve = rdrCtx.curve;

//        edges = edges_initial;
        edgesInt = edgesInt_initial;
        edgeBuckets = edgeBuckets_initial;
        edgeBucketCounts = edgeBucketCounts_initial;

        alphaLine = alphaLine_initial;

        this.cache = rdrCtx.piscesCache;

        // ScanLine:
        crossings = crossings_initial;
        edgePtrs = edgePtrs_initial;

        edgeCount = 0;
        activeEdgeMaxUsed = 0;
    }

    Renderer init(final int pix_boundsX, final int pix_boundsY,
                  final int pix_boundsWidth, final int pix_boundsHeight,
                  final int windingRule) {

        this.windingRule = windingRule;

        /* bounds as half-open intervals: minX <= x < maxX and minY <= y < maxY */
        this.boundsMinX =  pix_boundsX << SUBPIXEL_LG_POSITIONS_X;
        this.boundsMaxX = (pix_boundsX + pix_boundsWidth) << SUBPIXEL_LG_POSITIONS_X;
        this.boundsMinY =  pix_boundsY << SUBPIXEL_LG_POSITIONS_Y;
        this.boundsMaxY = (pix_boundsY + pix_boundsHeight) << SUBPIXEL_LG_POSITIONS_Y;
        
        if (doLogBounds) {
            PiscesUtils.logInfo("boundsXY = [" + boundsMinX + " ... " + boundsMaxX + "[ [" + boundsMinY + " ... " + boundsMaxY + "[");
        }

        /* see addLine: ceil(boundsMaxY) => boundsMaxY + 1 */
        final int edgeBucketsLength = (boundsMaxY - boundsMinY) + 1; // +1 for edgeBucketCounts 

        if (INITIAL_BUCKET_ARRAY < edgeBucketsLength) {
            edgeBuckets = rdrCtx.getIntArray(edgeBucketsLength);
            edgeBucketCounts = rdrCtx.getIntArray(edgeBucketsLength);
        }

        edgesPos = 0;        
        edgeMinY = Float.POSITIVE_INFINITY;
        edgeMaxY = Float.NEGATIVE_INFINITY;
        edgeMinX = Float.POSITIVE_INFINITY;
        edgeMaxX = Float.NEGATIVE_INFINITY;

        return this; // fluent API
    }

    /**
     * Disposes this renderer and recycle it clean up before reusing this instance
     */
    void dispose() {
        if (crossings != crossings_initial) {
            rdrCtx.putIntArray(crossings, activeEdgeMaxUsed);
            crossings = crossings_initial;
        }
        if (edgePtrs != edgePtrs_initial) {
            rdrCtx.putIntArray(edgePtrs, activeEdgeMaxUsed);
            edgePtrs = edgePtrs_initial;
        }
        if (doCleanDirty) {
            // LBO: keep crossings and edgePtrs dirty
            IntArrayCache.fill(crossings, 0, activeEdgeMaxUsed, 0);
            IntArrayCache.fill(edgePtrs,  0, activeEdgeMaxUsed, 0);
        }
        if (doStats) {
            rdrCtx.stat_rdr_activeEdges.add(activeEdgeMaxUsed);
        }
        edgeCount = 0;
        activeEdgeMaxUsed = 0;

        // Return arrays:
        if (doStats) {
            rdrCtx.stat_rdr_edges.add(edgesPos);
        }
/*        
        if (edges != edges_initial) {
            rdrCtx.putFloatArray(edges, edgesPos);
            edges = edges_initial;
        }
*/        
        if (edgesInt != edgesInt_initial) {
            rdrCtx.putIntArray(edgesInt, edgesPos);
            edgesInt = edgesInt_initial;
        }
        if (doCleanDirty) {
//            FloatArrayCache.fill(edges_initial, 0, edgesPos, 0);
            IntArrayCache.fill(edgesInt_initial, 0, edgesPos, 0);
        }
        if (alphaLine != alphaLine_initial) {
            rdrCtx.putIntArray(alphaLine, 0); // already zero filled
            alphaLine = alphaLine_initial;
        }

        if (edgeMinY != Float.POSITIVE_INFINITY) {
            // Find used part
            final int _boundsMinY = boundsMinY;
            final int iminY =     Math.max(FastMath.ceil(edgeMinY), _boundsMinY) - _boundsMinY;
            final int imaxY = 1 + Math.min(FastMath.ceil(edgeMaxY),  boundsMaxY) - _boundsMinY;

            if (edgeBuckets == edgeBuckets_initial) {
                IntArrayCache.fill(edgeBuckets,      iminY, imaxY - 1, 0); // fill only used part
                IntArrayCache.fill(edgeBucketCounts, iminY, imaxY,     0); // fill only used part
            } else {
                rdrCtx.putIntArray(edgeBuckets, imaxY);
                edgeBuckets = edgeBuckets_initial;

                rdrCtx.putIntArray(edgeBucketCounts, imaxY);
                edgeBucketCounts = edgeBucketCounts_initial;
            }
        } else if (edgeBuckets != edgeBuckets_initial) {
            // unused arrays
            rdrCtx.putIntArray(edgeBuckets, 0);      // unused array
            edgeBuckets = edgeBuckets_initial;

            rdrCtx.putIntArray(edgeBucketCounts, 0); // unused array
            edgeBucketCounts = edgeBucketCounts_initial;
        }
        if (doMonitors) {
            rdrCtx.mon_rdr_endRendering.stop();
            rdrCtx.mon_pre_getAATileGenerator.stop();
        }
        /* recycle the RendererContext instance */
        PiscesRenderingEngine.returnRendererContext(rdrCtx);
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
            float x3, float y3) {
        final float xe = tosubpixx(x3);
        final float ye = tosubpixy(y3);
        curve.set(x0, y0, tosubpixx(x1), tosubpixy(y1), tosubpixx(x2), tosubpixy(y2), xe, ye);
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

    /* clean alpha array (0 fill) */
    private int[] alphaLine;
    /* 4096 pixel large */
    private final int[] alphaLine_initial = new int[INITIAL_AA_ARRAY]; // 16K

    private void _endRendering(final int ymin, final int ymax) {

        // Get X bounds as true pixel boundaries to compute correct pixel coverage:
        final int bboxx0 = bbox_spminX;
        final int bboxx1 = bbox_spmaxX;

        // Mask to determine the relevant bit of the crossing sum
        // 0x1 if EVEN_ODD, all bits if NON_ZERO
        final int mask = (windingRule == WIND_EVEN_ODD) ? 0x1 : ~0x0;

        // Useful when processing tile line by tile line
        final int[] _alpha = alphaLine;

        // local vars (performance):
        final PiscesCache _cache = cache;
//        final float[] _edges     = edges;
        final int[] _edgesInt    = edgesInt;
        final int[] _edgeBuckets = edgeBuckets;
        final int[] _edgeBucketCounts = edgeBucketCounts;

        int[] _edgePtrs  = this.edgePtrs;
        int[] _crossings = this.crossings;

        // copy constants:
        final int _SLOPE = SLOPE;
        final int _NEXT  = NEXT;
        final int _YMAX  = YMAX;
        final int _OR    = OR;

        // unsafe I/O:
        final long _OFFSET = OFFSET;
//        final long _SIZE = SIZE;
        final Unsafe _unsafe = unsafe;
        long addr;
        final int _SUBPIXEL_LG_POSITIONS_X = SUBPIXEL_LG_POSITIONS_X;
        final int _SUBPIXEL_LG_POSITIONS_Y = SUBPIXEL_LG_POSITIONS_Y;
        final int _SUBPIXEL_MASK_X = SUBPIXEL_MASK_X;
        final int _SUBPIXEL_MASK_Y = SUBPIXEL_MASK_Y;
        final int _SUBPIXEL_POSITIONS_X = SUBPIXEL_POSITIONS_X;

        final int _MIN_VALUE = Integer.MIN_VALUE;
        final int _MAX_VALUE = Integer.MAX_VALUE;

        final int _THRESHOLD_BINARY_SEARCH = THRESHOLD_BINARY_SEARCH;

        // Now we iterate through the scanlines. We must tell emitRow the coord
        // of the first non-transparent pixel, so we must keep accumulators for
        // the first and last pixels of the section of the current pixel row
        // that we will emit.
        // We also need to accumulate pix_bbox*, but the iterator does it
        // for us. We will just get the values from it once this loop is done
        int pix_minX = _MAX_VALUE;
        int pix_maxX = _MIN_VALUE;

        int y = ymin;
        int bucket = y - boundsMinY;        

        int numCrossings = this.edgeCount;
        int edgePtrsLen = _edgePtrs.length;
        int crossingsLen = _crossings.length;
        int _arrayMaxUsed = activeEdgeMaxUsed;
        int ptrLen, newCount, ptrEnd;

        int bucketcount, i, j, ecur, lowx, highx;
        int cross, jcross, lastCross;
        float f_curx;
        int x0, x1, tmp, sum, prev, curx, crorientation, pix_x, pix_xmaxm1, pix_xmax;

        int low, high, mid, prevNumCrossings;
        boolean useBinarySearch;
        
        int lastY = -1; // last emited row
        

        for (; y < ymax; y++, bucket++) {
            // --- from former ScanLineIterator.next()
            bucketcount = _edgeBucketCounts[bucket];

            // marker on previously sorted edges:
            prevNumCrossings = numCrossings;

            // bucketCount indicates new edge / edge end:
            if (bucketcount != 0) {
                if (doStats) {
                    rdrCtx.stat_rdr_activeEdges_updates.add(numCrossings);
                }

                // last bit set to 1 means that edges ends
                if ((bucketcount & 0x1) != 0) {
                    // eviction in active edge list
                    newCount = 0;
                    for (i = 0; i < numCrossings; i++) {
                        ecur = _edgePtrs[i];
/*                        
                        if (_edgesInt[ecur + _YMAX] > y) {
                            _edgePtrs[newCount++] = ecur;
                        }
*/                        
                        // random access so use unsafe:
                        if (_unsafe.getInt(_edgesInt, _OFFSET + ((ecur + _YMAX) << 2)) > y) {
                            _edgePtrs[newCount++] = ecur;
                        }
                    }
                    // update marker on sorted edges minus removed edges:
                    prevNumCrossings = numCrossings = newCount;
                }

                ptrLen = bucketcount >> 1; // number of new edge
                
                if (ptrLen != 0) {
                    ptrEnd = numCrossings + ptrLen;

                    if (edgePtrsLen < ptrEnd) {
                        final boolean ptrInitial = (_edgePtrs == edgePtrs_initial);
                        this.edgePtrs = _edgePtrs = Helpers.widenArray(rdrCtx, _edgePtrs, numCrossings, ptrLen, _arrayMaxUsed);
                        if (ptrInitial && doCleanDirty) {
                            IntArrayCache.fill(edgePtrs_initial, 0, _arrayMaxUsed, 0);
                        }
                        edgePtrsLen = _edgePtrs.length;
                    }

                    // add new edges to active edge list:
                    for (ecur = _edgeBuckets[bucket]; numCrossings < ptrEnd; numCrossings++) {
                        _edgePtrs[numCrossings] = ecur;
//                        ecur = _edgesInt[ecur /* + NEXT */];
//                        ecur = _edgesInt[ecur + _NEXT ];
                        
                        // random access so use unsafe:
                        ecur = _unsafe.getInt(_edgesInt, _OFFSET + ((ecur + _NEXT) << 2));
                    }
                    
                    if (crossingsLen < numCrossings) {
                        if (_crossings == crossings_initial) {
                            // LBO: keep crossings dirty
                            if (doCleanDirty) {
                                IntArrayCache.fill(_crossings, 0, _arrayMaxUsed, 0);
                            }
                        } else {
                            rdrCtx.putIntArray(_crossings, _arrayMaxUsed); // last known value for arrayMaxUsed
                        }
                        // Get larger array:
                        this.crossings = _crossings = rdrCtx.getIntArray(numCrossings); // count or ptrs.length ?
                        crossingsLen = _crossings.length;
                    }
                    // LBO: max used mark
                    if (numCrossings > _arrayMaxUsed) {
                        _arrayMaxUsed = numCrossings;
                    }
                } // ptrLen != 0
            } // bucketCount != 0

            if (numCrossings != 0) {
                // try avoid sorting loop:
                useBinarySearch = USE_BINARY_SEARCH && (numCrossings >= _THRESHOLD_BINARY_SEARCH);

                lastCross = _MIN_VALUE;

                for (i = 0; i < numCrossings; i++) {
                    ecur = _edgePtrs[i];
//                    f_curx = _edges[ecur /* + F_CURX */];
// 2=floats to ints                    
//                    f_curx = Float.intBitsToFloat(_edgesInt[ecur /* + F_CURX */]);
// 3=unsafe
                    // random access so use unsafe:
                    addr = _OFFSET + (ecur << 2); /* ecur + F_CURX */
                    
                    /* TODO: load 1 double=2xfloats=[F_CURX SLOPE] */
                    
                    f_curx = _unsafe.getFloat(_edgesInt, addr);
                    

                    /* convert subpixel coordinates (float) into pixel positions (int) for coming scanline */
                    /* note: it is faster to always update edges even if it is removed from AEL for coming or last scanline */
//                    _edges[ecur /* + F_CURX */] = f_curx + _edges[ecur + _SLOPE];
// 2=floats to ints                    
//                    _edgesInt[ecur /* + F_CURX */] = Float.floatToRawIntBits(f_curx + Float.intBitsToFloat(_edgesInt[ecur + _SLOPE]));
// 3=unsafe
                    // random access so use unsafe:
                    _unsafe.putFloat(_edgesInt, addr, f_curx + _unsafe.getFloat(_edgesInt, addr + 4)); /* ecur + _SLOPE */

                    
                    /* TODO: try split loops: update crossings and sort them to have better cache affinity (seems slower) */

                    // update edge:
                    cross = (int) f_curx;

                    /* TODO: revert changes concerning orientation => sort with crossings (may be important) */

                    // TODO: Try to avoid sorting edges at each scanline (few intersection)
                    // example: simple line: 2 parallel edges => sort once !!
                    // => work with active edges Ptr only (not crossing)
                    // => sort edges (DDA also)
                    // insertion sort of crossings:
                    if (doStats) {
                        rdrCtx.stat_rdr_crossings_updates.add(numCrossings);
                    }

                    if (cross < lastCross) {
                        if (doStats) {
                            rdrCtx.stat_rdr_crossings_sorts.add(i);
                        }
                        // TODO: try using more efficient quick-sort on edgePtrs but compararing crossings (matched arrays) !
                        
                        /* TODO: presort edge stored as linked list ? (see addLine) */
                        
                        /* split loops => have 2 loops (almost sorted and new edges handled apart */
                        if (useBinarySearch && (i >= prevNumCrossings)) { 
                            if (doStats) {
                                rdrCtx.stat_rdr_crossings_bsearch.add(i);
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
                            _crossings[j + 1] = cross;
                            _edgePtrs [j + 1] = ecur;

                        } else {
                            j = i - 1;
                            _crossings[i] = _crossings[j];
                            _edgePtrs[i] = _edgePtrs[j];

                            while (--j >= 0) {
                                jcross = _crossings[j];
                                if (jcross <= cross) {
                                    break;
                                }
                                /* cross < jcross; swap element */
                                _crossings[j + 1] = jcross;
                                _edgePtrs [j + 1] = _edgePtrs[j];
                            }
                            _crossings[j + 1] = cross;
                            _edgePtrs [j + 1] = ecur;
                        }

                    } else {
                        _crossings[i] = lastCross = cross;
                    }
                }
                // --- from former ScanLineIterator.next()

                // note: right shift on crossing to get x crossing: NO MORE!
                /* TODO: revert changes concerning orientation => sort with crossings (may be important) */
                
                lowx = _crossings[0];
                highx = _crossings[numCrossings - 1];
                
                /* note: bboxx0 and bboxx1 must be pixel boundaries to have correct coverage computation */
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

                // TODO: fix alpha last index = pix_xmax + 1
                // ie alpha[x1 >> SUBPIXEL_LG_POSITIONS_X +1 ] = cross (inclusive)
                for (i = 0, sum = 0, prev = bboxx0; i < numCrossings; i++) {
                    ecur = _edgePtrs [i];
                    curx = _crossings[i];

//                    crorientation = _edgesInt[ecur + _OR];
 
                    // random access so use unsafe:
                    crorientation = _unsafe.getInt(_edgesInt, _OFFSET + ((ecur + _OR) << 2));
                        

                    if ((sum & mask) != 0) {
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
                                _alpha[pix_x    ] += (_SUBPIXEL_POSITIONS_X - tmp);
                                _alpha[pix_x + 1] += tmp;

                                pix_xmax = x1 >> _SUBPIXEL_LG_POSITIONS_X;

                                tmp = (x1 & _SUBPIXEL_MASK_X);
                                _alpha[pix_xmax    ] -= (_SUBPIXEL_POSITIONS_X - tmp);
                                _alpha[pix_xmax + 1] -= tmp;
                            }
                        }
                    }

                    /* TODO: revert changes concerning orientation => sort with crossings (may be important) */
                    sum += crorientation;
                    prev = curx;
                }
            }

            // even if this last row had no crossings, alpha will be zeroed
            // from the last emitRow call. But this doesn't matter because
            // maxX < minX, so no row will be emitted to the piscesCache.
            if ((y & _SUBPIXEL_MASK_Y) == _SUBPIXEL_MASK_Y) {
                lastY = y >> _SUBPIXEL_LG_POSITIONS_Y;
                if (pix_maxX >= pix_minX) {
                    // note: alpha array will be zeroed by copyAARow()
                    // +2 because alpha [pix_minX; pix_maxX+1]
                    _cache.copyAARow(_alpha, lastY, pix_minX, pix_maxX + 2); // fix range [x0; x1[
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
            _cache.copyAARow(_alpha, y, pix_minX, pix_maxX + 2); // fix range [x0; x1[
        } else if (y != lastY) {
            _cache.clearAARow(y);
        }

        // update member:
        edgeCount = numCrossings;

        // LBO: max used mark
        activeEdgeMaxUsed = _arrayMaxUsed;
    }

    boolean endRendering() {
        // TODO: perform shape clipping to avoid dealing with segments out of bounding box
        if (edgeMinY == Float.POSITIVE_INFINITY) {
            return false; // undefined edges bounds
        }

        /* TODO: improve accuracy using correct float rounding to int ie use ceil(x - 0.5f) */
        
        /* bounds as inclusive intervals */
        final int spminX = Math.max(FastMath.ceil(edgeMinX), boundsMinX);
        final int spmaxX = Math.min(FastMath.ceil(edgeMaxX), boundsMaxX - 1);
        final int spminY = Math.max(FastMath.ceil(edgeMinY), boundsMinY);
        final int spmaxY = Math.min(FastMath.ceil(edgeMaxY), boundsMaxY - 1);

        if (doLogBounds) {
            PiscesUtils.logInfo("edgesXY = [" + edgeMinX + " ... " + edgeMaxX + "][" + edgeMinY + " ... " + edgeMaxY + "]");
            PiscesUtils.logInfo("spXY    = [" + spminX + " ... " + spmaxX + "][" + spminY + " ... " + spmaxY + "]");
        }

        /* test clipping for shapes out of bounds */
        if ((spminX > spmaxX) || (spminY > spmaxY)) { 
            return false;
        }
        
        /* half open intervals */
        final int pminX =  spminX                    >> SUBPIXEL_LG_POSITIONS_X; // inclusive
        final int pmaxX = (spmaxX + SUBPIXEL_MASK_X) >> SUBPIXEL_LG_POSITIONS_X; // exclusive
        final int pminY =  spminY                    >> SUBPIXEL_LG_POSITIONS_Y; // inclusive
        final int pmaxY = (spmaxY + SUBPIXEL_MASK_Y) >> SUBPIXEL_LG_POSITIONS_Y; // exclusive

        // store BBox to answer ptg.getBBox():
        this.cache.init(pminX, pminY, pmaxX, pmaxY);

        // LBO: memorize the rendering bounding box:
        /* note: bbox_spminX and bbox_spmaxX must be pixel boundaries to have correct coverage computation */
        bbox_spminX = pminX << SUBPIXEL_LG_POSITIONS_X; // inclusive
        bbox_spmaxX = pmaxX << SUBPIXEL_LG_POSITIONS_X; // exclusive
        bbox_spminY = spminY; // inclusive
        bbox_spmaxY = Math.min(spmaxY + 1, pmaxY << SUBPIXEL_LG_POSITIONS_Y); // exclusive

        if (doLogBounds) {
            PiscesUtils.logInfo("pXY       = [" + pminX + " ... " + pmaxX + "[ [" + pminY + " ... " + pmaxY + "[");
            PiscesUtils.logInfo("bbox_spXY = [" + bbox_spminX + " ... " + bbox_spmaxX + "[ [" + bbox_spminY + " ... " + bbox_spmaxY + "[");
        }
        
        // Prepare alpha line:
        // add 2 to better deal with the last pixel in a pixel row.
        final int width = (pmaxX - pminX) + 2;

        // Useful when processing tile line by tile line
        if (width > INITIAL_AA_ARRAY) {
            alphaLine = rdrCtx.getIntArray(width);
        }

        if (doMonitors) {
            rdrCtx.mon_rdr_endRendering.start();
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

            // LBO: hack to process tile line [0 - 32]
            cache.resetTileLine(pminY);

            if (doMonitors) {
                rdrCtx.mon_rdr_endRendering_Y.start();
            }
            
            // Process only one tile line:
            _endRendering(fixed_spminY, spmaxY);

            if (doMonitors) {
                rdrCtx.mon_rdr_endRendering_Y.stop();
            }
        }
    }
}
