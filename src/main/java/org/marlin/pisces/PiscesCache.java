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

import java.util.Arrays;

/**
 * An object used to cache pre-rendered complex paths.
 *
 * @see PiscesRenderer#render
 */
public final class PiscesCache implements PiscesConst {
    /* constants */
    public static final int TILE_SIZE_LG = PiscesRenderingEngine.getTileSize_Log2();
    public static final int TILE_SIZE = 1 << TILE_SIZE_LG; // 32 by default

    /* 2048 alpha values (width) x 32 rows (tile) = 256K */
    static final int INITIAL_CHUNK_ARRAY = TILE_SIZE * INITIAL_PIXEL_DIM;
    
    /* members */
    int bboxX0, bboxY0, bboxX1, bboxY1;

    /* row index in rowAAChunk[] (dirty 1D array) */
    final int[] rowAAChunkIndex = new int[TILE_SIZE];

    /* 1D dirty array containing 32 rows (packed) */
    // rowAAStride[i] holds the encoding of the pixel row with y = bboxY0+i.
    // The format of each of the inner arrays is: rowAAStride[i][0,1] = (x0, n)
    // where x0 is the first x in row i with nonzero alpha, and n is the
    // number of RLE entries in this row. rowAAStride[i][j,j+1] for j>1 is
    // (val,runlen)
    /* LBO: TODO: fix doc (no RLE anymore) */
    int[] rowAAChunk;
    /* current position in rowAAChunk array */
    int rowAAChunkPos;

    // touchedTile[i] is the sum of all the alphas in the tile with
    // x=j*TILE_SIZE+bboxX0.
    private int[] touchedTile;

    /** per-thread renderer context */
    final RendererContext rdrCtx;

    /** large cached rowAAChunk (dirty) */
    final int[] rowAAChunk_initial;
    /** large cached touchedTile (dirty) */
    final int[] touchedTile_initial;

    int tileMin, tileMax;

    PiscesCache(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        // +1 to avoid recycling in widenDirtyIntArray()
        this.rowAAChunk_initial  = new int[INITIAL_CHUNK_ARRAY + 1]; // 256K
        this.touchedTile_initial = new int[INITIAL_ARRAY];       // only 1 tile line
        
        rowAAChunk  = rowAAChunk_initial;
        touchedTile = touchedTile_initial;

        // tile used marks:
        tileMin = Integer.MAX_VALUE;
        tileMax = Integer.MIN_VALUE;
    }

    void init(int minx, int miny, int maxx, int maxy) {
        // assert maxy >= miny && maxx >= minx;
        bboxX0 = minx;
        bboxY0 = miny;
        bboxX1 = maxx;
        bboxY1 = maxy;

        // the ceiling of (maxy - miny + 1) / TILE_SIZE;
        final int nxTiles = (maxx - minx + TILE_SIZE) >> TILE_SIZE_LG;

        if (nxTiles > INITIAL_ARRAY) {
            touchedTile = rdrCtx.getIntArray(nxTiles);
        }
    }

    /**
     * Disposes this renderer:
     * clean up before reusing this instance
     */
    void dispose() {
        // Reset touchedTile if needed:
        resetTileLine(0);

        // Return arrays:
        if (rowAAChunk != rowAAChunk_initial) {
            rdrCtx.putDirtyIntArray(rowAAChunk);
            rowAAChunk = rowAAChunk_initial;
        }
        if (touchedTile != touchedTile_initial) {
            rdrCtx.putIntArray(touchedTile, 0, 0); // already zero filled
            touchedTile = touchedTile_initial;
        }
    }

    void resetTileLine(final int pminY) {
        // LBO: hack to process tile line [0 - 32]
        bboxY0 = pminY;

        /* reset current pos */
        if (doStats) {
            this.rdrCtx.stat_cache_rowAAChunk.add(rowAAChunkPos);
        }
        rowAAChunkPos = 0;

        // Reset touchedTile:
        if (tileMin != Integer.MAX_VALUE) {
            // clean only dirty touchedTile:
            if (tileMax == 1) {
                touchedTile[0] = 0;
            } else {
                IntArrayCache.fill(touchedTile, tileMin, tileMax, 0);
            }
            // reset tile used marks:
            tileMin = Integer.MAX_VALUE;
            tileMax = Integer.MIN_VALUE;
        }

        if (doCleanDirty) {
            Arrays.fill(rowAAChunk, 0);
        }
    }

    void clearAARow(final int y) {
        // LBO: hack to process tile line [0 - 32]
        final int row = y - bboxY0;
        
        // get current position:
        final int pos = rowAAChunkPos;
        // update row index to current position:
        rowAAChunkIndex[row] = pos;
        // update row data:
        int[] _rowAAChunk = rowAAChunk;
        // ensure rowAAChunk capacity:
        if (_rowAAChunk.length < pos + 2) {
            rowAAChunk = _rowAAChunk = rdrCtx.widenDirtyIntArray(_rowAAChunk, pos, 2);
        }
        // update row data:
        _rowAAChunk[pos    ] = 0; // first pixel inclusive
        _rowAAChunk[pos + 1] = 0; //  last pixel exclusive
        // update pos:
        rowAAChunkPos = pos + 2;
    }

    /**
     * Copy the given alpha data into the rowAA cache
     * @param alphaRow alpha data to copy from
     * @param y y pixel coordinate
     * @param px0 first pixel inclusive x0
     * @param px1 last pixel exclusive x1
     */
    void copyAARow(final int[] alphaRow, final int y, final int px0, final int px1) {
        if (doMonitors) {
            rdrCtx.mon_rdr_emitRow.start();
        }

        /* skip useless pixels above boundary */
        final int px_bbox1 = Math.min(px1, bboxX1);
    
        if (doLogBounds) {
            PiscesUtils.logInfo("row = [" + px0 + " ... " + px_bbox1 + " ("+px1+") [ for y=" + y);
        }
        
        final int row = y - bboxY0;
        
        // get current position:
        final int pos = rowAAChunkPos;
        // update row index to current position:
        rowAAChunkIndex[row] = pos;
        // update row data:
        int[] _rowAAChunk = rowAAChunk;
        // ensure rowAAChunk capacity:
        if (_rowAAChunk.length < pos + 2 + (px_bbox1 - px0)) {
            rowAAChunk = _rowAAChunk = rdrCtx.widenDirtyIntArray(_rowAAChunk, pos, 2 + (px_bbox1 - px0));
        }
        if (doStats) {
            this.rdrCtx.stat_cache_rowAA.add(2 + px_bbox1 - px0);
        }
        // rowAA contains (x0 x1)(alpha values for range[x0; x1[)
        _rowAAChunk[pos    ] = px0;      // first pixel inclusive
        _rowAAChunk[pos + 1] = px_bbox1; //  last pixel exclusive

        final int from = px0      - bboxX0; // first pixel inclusive
        final int to   = px_bbox1 - bboxX0; //  last pixel exclusive

        final int[] touchedLine = touchedTile;
        final int _TILE_SIZE_LG = TILE_SIZE_LG;

        // fix offset in rowAAChunk:
        final int off = pos + 2 - from;
        
        // compute alpha sum into rowAA:
        for (int x = from, val = 0; x < to; x++) {
            val += alphaRow[x]; // [from; to[

            /* ensure val is [0;64] */
//            val &= _MASK_ALPHA_COVERAGE; /* use alpha mask to ensure values are in [0;64] range */

            // store alpha sum:
            _rowAAChunk[x + off] = val;

            if (val != 0) {
                // update touchedTile
                touchedLine[x >> _TILE_SIZE_LG] += val;
            }
        }
        
        // update current position:
        rowAAChunkPos = pos + 2 + px_bbox1 - px0;

        // update tile used marks:
        int tx = from >> _TILE_SIZE_LG; // inclusive
        if (tx < tileMin) {
            tileMin = tx;
        }

        tx = ((to - 1) >> _TILE_SIZE_LG) + 1; // exclusive (+1 to be sure)
        if (tx > tileMax) {
            tileMax = tx;
        }

        if (doLogBounds) {
            PiscesUtils.logInfo("clear = [" + from + " ... " + to + "[");
        }
        
        // Clear alpha row for reuse:
        IntArrayCache.fill(alphaRow, from, px1 - bboxX0, 0);
        
        if (doMonitors) {
            rdrCtx.mon_rdr_emitRow.stop();
        }
    }

    int alphaSumInTile(final int x) {
        // LBO: hack to process tile line [0 - 32]
        return touchedTile[(x - bboxX0) >> TILE_SIZE_LG];
    }

    @Override
    public String toString() {
        String ret = "bbox = ["
                + bboxX0 + ", " + bboxY0 + " => "
                + bboxX1 + ", " + bboxY1 + "]\n";
        
        for (int pos : rowAAChunkIndex) {
            ret += ("minTouchedX=" + rowAAChunk[pos]
                    + "\tRLE Entries: " + Arrays.toString(
                            Arrays.copyOfRange(rowAAChunk, pos + 2, pos + rowAAChunk[pos + 1])) + "\n");
        }
        return ret;
    }
}
