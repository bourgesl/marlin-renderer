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
package org.marlin.pisces;

import java.util.Arrays;

/**
 * An object used to cache pre-rendered complex paths.
 *
 * @see Renderer
 */
public final class MarlinCache implements MarlinConst {

    private final static double GAMMA = MarlinProperties.getGamma();

    // 2048 (pixelSize) alpha values (width) x 32 rows (tile) = 64K
    private static final int INITIAL_CHUNK_ARRAY = TILE_SIZE * INITIAL_PIXEL_DIM;

    // The alpha map used by this object (taken out of our map cache) to convert
    // pixel coverage counts gotten from MarlinCache (which are in the range
    // [0, maxalpha]) into alpha values, which are in [0,256).
    private final static byte[] ALPHA_MAP = buildAlphaMap(MAX_AA_ALPHA);

    protected int bboxX0, bboxY0, bboxX1, bboxY1;

    // 1D dirty arrays
    // row index in rowAAChunk[]
    protected final int[] rowAAChunkIndex = new int[TILE_SIZE];
    // first pixel (inclusive) for each row
    protected final int[] rowAAx0 = new int[TILE_SIZE];
    // last pixel (exclusive) for each row
    protected final int[] rowAAx1 = new int[TILE_SIZE];

    // 1D dirty array containing pixel coverages for (32) rows (packed)
    // use rowAAx0/rowAAx1 to get row indices within this byte chunk
    protected byte[] rowAAChunk;
    // current position in rowAAChunk array
    private int rowAAChunkPos;

    // touchedTile[i] is the sum of all the alphas in the tile with
    // x=j*TILE_SIZE+bboxX0.
    private int[] touchedTile;

    // per-thread renderer context
    private final RendererContext rdrCtx;

    // large cached rowAAChunk (dirty)
    // +1 to avoid recycling in widenDirtyIntArray()
    private final byte[] rowAAChunk_initial = new byte[INITIAL_CHUNK_ARRAY + 1]; // 64K
    // large cached touchedTile (dirty)
    private final int[] touchedTile_initial = new int[INITIAL_ARRAY]; // 1 tile line

    private int tileMin, tileMax;

    MarlinCache(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

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
            if (DO_STATS) {
                RendererContext.stats.stat_array_marlincache_touchedTile
                    .add(nxTiles);
            }
            touchedTile = rdrCtx.getIntArray(nxTiles);
        }
    }

    /**
     * Disposes this cache:
     * clean up before reusing this instance
     */
    void dispose() {
        // Reset touchedTile if needed:
        resetTileLine(0);

        // Return arrays:
        if (rowAAChunk != rowAAChunk_initial) {
            rdrCtx.putDirtyByteArray(rowAAChunk);
            rowAAChunk = rowAAChunk_initial;
        }
        if (touchedTile != touchedTile_initial) {
            rdrCtx.putIntArray(touchedTile, 0, 0); // already zero filled
            touchedTile = touchedTile_initial;
        }
    }

    void resetTileLine(final int pminY) {
        // update bboxY0 to process a complete tile line [0 - 32]
        bboxY0 = pminY;

        // reset current pos
        if (DO_STATS) {
            RendererContext.stats.stat_cache_rowAAChunk.add(rowAAChunkPos);
        }
        rowAAChunkPos = 0;

        // Reset touchedTile:
        if (tileMin != Integer.MAX_VALUE) {
            if (DO_STATS) {
                RendererContext.stats.stat_cache_tiles.add(tileMax - tileMin);
            }
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

        if (DO_CLEAN_DIRTY) {
            // Force zero-fill dirty arrays:
            Arrays.fill(rowAAChunk, BYTE_0);
        }
    }

    void clearAARow(final int y) {
        // process tile line [0 - 32]
        final int row = y - bboxY0;

        // update pixel range:
        rowAAx0[row] = 0; // first pixel inclusive
        rowAAx1[row] = 0; //  last pixel exclusive

        // note: leave rowAAChunkIndex[row] undefined
    }

    /**
     * Copy the given alpha data into the rowAA cache
     * @param alphaRow alpha data to copy from
     * @param y y pixel coordinate
     * @param px0 first pixel inclusive x0
     * @param px1 last pixel exclusive x1
     */
    void copyAARow(final int[] alphaRow, final int y,
                   final int px0, final int px1)
    {
        if (DO_MONITORS) {
            RendererContext.stats.mon_rdr_copyAARow.start();
        }

        // skip useless pixels above boundary
        final int px_bbox1 = Math.min(px1, bboxX1);

        if (DO_LOG_BOUNDS) {
            MarlinUtils.logInfo("row = [" + px0 + " ... " + px_bbox1
                                + " (" + px1 + ") [ for y=" + y);
        }

        final int row = y - bboxY0;

        // update pixel range:
        rowAAx0[row] = px0;      // first pixel inclusive
        rowAAx1[row] = px_bbox1; //  last pixel exclusive

        final int len = px_bbox1 - px0;

        // get current position:
        final int pos = rowAAChunkPos;
        // update row index to current position:
        rowAAChunkIndex[row] = pos;
        // update row data:
        byte[] _rowAAChunk = rowAAChunk;
        // ensure rowAAChunk capacity:
        if (_rowAAChunk.length < pos + len) {
            if (DO_STATS) {
                RendererContext.stats.stat_array_marlincache_rowAAChunk
                    .add(pos + len);
            }
            rowAAChunk = _rowAAChunk
                = rdrCtx.widenDirtyByteArray(_rowAAChunk, pos, pos + len);
        }
        if (DO_STATS) {
            RendererContext.stats.stat_cache_rowAA.add(len);
        }

        // rowAA contains only alpha values for range[x0; x1[

        final int from = px0      - bboxX0; // first pixel inclusive
        final int to   = px_bbox1 - bboxX0; //  last pixel exclusive

        final int[] touchedLine = touchedTile;
        final int _TILE_SIZE_LG = TILE_SIZE_LG;
        final byte[] _ALPHA_MAP = ALPHA_MAP;

        // fix offset in rowAAChunk:
        final int off = pos - from;

        // compute alpha sum into rowAA:
        for (int x = from, val = 0; x < to; x++) {
            // alphaRow is in [0; MAX_COVERAGE]
            val += alphaRow[x]; // [from; to[

            // ensure values are in [0; MAX_AA_ALPHA] range
            if (DO_AA_RANGE_CHECK) {
                if (val < 0) {
                    System.out.println("Invalid coverage = " + val);
                    val = 0;
                }
                if (val > MAX_AA_ALPHA) {
                    System.out.println("Invalid coverage = " + val);
                    val = MAX_AA_ALPHA;
                }
            }

            // TODO: better int to byte conversion (filter normalization)

            // store alpha sum (as byte):
            _rowAAChunk[x + off] = _ALPHA_MAP[val];

            if (val != 0) {
                // update touchedTile
                touchedLine[x >> _TILE_SIZE_LG] += val;
            }
        }

        // update current position:
        rowAAChunkPos = pos + len;

        // update tile used marks:
        int tx = from >> _TILE_SIZE_LG; // inclusive
        if (tx < tileMin) {
            tileMin = tx;
        }

        tx = ((to - 1) >> _TILE_SIZE_LG) + 1; // exclusive (+1 to be sure)
        if (tx > tileMax) {
            tileMax = tx;
        }

        if (DO_LOG_BOUNDS) {
            MarlinUtils.logInfo("clear = [" + from + " ... " + to + "[");
        }

        // Clear alpha row for reuse:
        IntArrayCache.fill(alphaRow, from, px1 - bboxX0, 0);

        if (DO_MONITORS) {
            RendererContext.stats.mon_rdr_copyAARow.stop();
        }
    }

    int alphaSumInTile(final int x) {
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
                            Arrays.copyOfRange(rowAAChunk, pos + 2,
                                               pos + rowAAChunk[pos + 1]))
                    + "\n");
        }
        return ret;
    }

    private static byte[] buildAlphaMap(final int maxalpha) {
        // double size !
        final byte[] alMap = new byte[maxalpha << 1];
        final int halfmaxalpha = maxalpha >> 2;
        for (int i = 0; i <= maxalpha; i++) {
            alMap[i] = (byte) ((i * 255 + halfmaxalpha) / maxalpha);
//            System.out.println("alphaMap[" + i + "] = "
//                               + Byte.toUnsignedInt(alMap[i]));
        }

        if (GAMMA != 1.0) {
//            System.out.println("alphaMap[gamma = " + GAMMA + "]");
            final double invGamma = 1.0 / GAMMA;
            final double max = (double)maxalpha;

            for (int i = 0; i <= maxalpha; i++) {
                alMap[i] = (byte) (0xFF * Math.pow(i / max, invGamma));
//                System.out.println("alphaMap[" + i + "] = " + PiscesUtils.toUnsignedInt(alMap[i]) + " :: "+Math.pow(i / (double)maxalpha, 1d / GAMMA));
            }
        }

        return alMap;
    }
}
