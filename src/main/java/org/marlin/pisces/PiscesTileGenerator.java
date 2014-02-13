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

import sun.java2d.pipe.AATileGenerator;

final class PiscesTileGenerator implements AATileGenerator, PiscesConst {

    private static final int TILE_SIZE = PiscesCache.TILE_SIZE;

    private final static int maxTileAlphaSum = TILE_SIZE * TILE_SIZE * Renderer.MAX_AA_ALPHA;

    // The alpha map used by this object (taken out of our map cache) to convert
    // pixel coverage counts gotten from PiscesCache (which are in the range
    // [0, maxalpha]) into alpha values, which are in [0,256).
    private final static byte[] alphaMap = buildAlphaMap(Renderer.MAX_AA_ALPHA);

    /* PiscesTileGenerator members */
    private final Renderer rdr;
    private final PiscesCache cache;
    private int x, y;

    PiscesTileGenerator(Renderer r) {
        this.rdr = r;
        this.cache = r.cache;
    }

    PiscesTileGenerator init() {
        this.x = cache.bboxX0;
        this.y = cache.bboxY0;

        return this; // fluent API
    }

    /**
     * Disposes this tile generator:
     * clean up before reusing this instance
     */
    @Override
    public void dispose() {
        // dispose cache:
        this.cache.dispose();
        // dispose renderer and calls returnRendererContext(rdrCtx)
        this.rdr.dispose();
    }

    private static byte[] buildAlphaMap(int maxalpha) {
        byte[] alMap = new byte[maxalpha + 1];
        int halfmaxalpha = maxalpha >> 2;
        for (int i = 0; i <= maxalpha; i++) {
            alMap[i] = (byte) ((i * 255 + halfmaxalpha) / maxalpha);
        }
        return alMap;
    }

    void getBbox(int bbox[]) {
        bbox[0] = cache.bboxX0;
        bbox[1] = cache.bboxY0;
        bbox[2] = cache.bboxX1;
        bbox[3] = cache.bboxY1;
    }

    /**
     * Gets the width of the tiles that the generator batches output into.
     * @return the width of the standard alpha tile
     */
    @Override
    public int getTileWidth() {
        return TILE_SIZE;
    }

    /**
     * Gets the height of the tiles that the generator batches output into.
     * @return the height of the standard alpha tile
     */
    @Override
    public int getTileHeight() {
        return TILE_SIZE;
    }

    /**
     * Gets the typical alpha value that will characterize the current
     * tile.
     * The answer may be 0x00 to indicate that the current tile has
     * no coverage in any of its pixels, or it may be 0xff to indicate
     * that the current tile is completely covered by the path, or any
     * other value to indicate non-trivial coverage cases.
     * @return 0x00 for no coverage, 0xff for total coverage, or any other
     *         value for partial coverage of the tile
     */
    @Override
    public int getTypicalAlpha() {
        int al = cache.alphaSumInTile(x);
        // Note: if we have a filled rectangle that doesn't end on a tile
        // border, we could still return 0xff, even though al!=maxTileAlphaSum
        // This is because if we return 0xff, our users will fill a rectangle
        // starting at x,y that has width = Math.min(TILE_SIZE, bboxX1-x),
        // and height min(TILE_SIZE,bboxY1-y), which is what should happen.
        // However, to support this, we would have to use 2 Math.min's
        // and 2 multiplications per tile, instead of just 2 multiplications
        // to compute maxTileAlphaSum. The savings offered would probably
        // not be worth it, considering how rare this case is.
        // Note: I have not tested this, so in the future if it is determined
        // that it is worth it, it should be implemented. Perhaps this method's
        // interface should be changed to take arguments the width and height
        // of the current tile. This would eliminate the 2 Math.min calls that
        // would be needed here, since our caller needs to compute these 2
        // values anyway.
        return (al == 0x00 ? 0x00
                : (al == maxTileAlphaSum ? 0xff : 0x80));
    }

    /**
     * Skips the current tile and moves on to the next tile.
     * Either this method, or the getAlpha() method should be called
     * once per tile, but not both.
     */
    @Override
    public void nextTile() {
        if ((x += TILE_SIZE) >= cache.bboxX1) {
            x = cache.bboxX0;
            y += TILE_SIZE;

            if (y < cache.bboxY1) {
                // LBO: compute rowAAStride for the tile line
                // [ y; max(y + TILE_SIZE, bboxY1) ]
                this.rdr.endRendering(y);
            }
        }
    }

    /**
     * Gets the alpha coverage values for the current tile.
     * Either this method, or the nextTile() method should be called
     * once per tile, but not both.
     */
    @Override
    public void getAlpha(final byte tile[], final int offset, final int rowstride) {
        if (doMonitors) {
            rdr.rdrCtx.mon_ptg_getAlpha.start();
        }

        // local vars for performance:
        final int[] rowAAChunkIndex = cache.rowAAChunkIndex;
        final int[] rowAAChunk = cache.rowAAChunk;
        final byte[] _alphaMap = alphaMap;

        int x0 = this.x;
        int x1 = x0 + TILE_SIZE;
        int y0 = this.y;
        int y1 = y0 + TILE_SIZE;
        if (x1 > cache.bboxX1) {
            x1 = cache.bboxX1;
        }
        if (y1 > cache.bboxY1) {
            y1 = cache.bboxY1;
        }

        if (doLogBounds) {
            PiscesUtils.logInfo("getAlpha = [" + x0 + " ... " + x1 + "[ [" + y0 + " ... " + y1 + "[");
        }
        
        final int skipRowPixels = (rowstride - (x1 - x0));

        // LBO: hack to process tile line [0 - 32[
        y1 -= y0;
        y0 = 0;

        int idx = offset;
        
        for (int cy = y0; cy < y1; cy++) {
            // get row index:
            final int pos = rowAAChunkIndex[cy];

            // empty line (default)
            int cx = x0;

            final int aax1 = rowAAChunk[pos + 1]; // exclusive            

            // quick check if there is AA data
            // corresponding to this tile [x0; x1[
            if (aax1 > x0) {
                final int aax0 = rowAAChunk[pos]; // inclusive

                if (aax0 < x1) {
                    // note: cx is the cursor pointer in the tile array (left to right)
                    cx = aax0;

                    // ensure cx >= x0
                    if (cx <= x0) {
                        cx = x0;
                    } else {
                        // fill line start until first AA pixel aax0 exclusive:
                        for (final int end = idx + (cx - x0); idx < end; idx++) {
                            tile[idx] = 0;
                        }
                    }

                    // now: cx >= x0 but cx < aax0 (x1 < aax0)
                    // Copy AA data (sum alpha data):
                    final int off = pos + 2 - aax0;

                    for (final int end = Math.min(aax1, x1); cx < end; cx++, idx++) {
                        final int aa = rowAAChunk[cx + off];
/*

                        if (DO_AA_RANGE_CHECK) {
                            // disable once regression tests are OK
                            if (aa > Renderer.MAX_AA_ALPHA) {
                                logInfo("Invalid AA = " + aa + " for cx = " + cx + ", cy = " + cy);
                                aa = Renderer.MAX_AA_ALPHA;
                            }
                            if (aa < 0) {
                                logInfo("Invalid AA = " + aa + " for cx = " + cx + ", cy = " + cy);
                                aa = 0;
                            }
                        }
*/
                        // cx inside tile[x0; x1[ and aa range:
                        tile[idx] = _alphaMap[aa];
                    }
                }
            }

            // fill line end
            for (final int end = idx + (x1 - cx); idx < end; idx++) {
                tile[idx] = 0;
            }

            if (doTrace) {
                for (int i = idx - (x1 - x0); i < idx; i++) {
                    System.out.print(hex(tile[i], 2));
                }
                System.out.println();
            }

            idx += skipRowPixels;
        }

        nextTile();

        if (doMonitors) {
            rdr.rdrCtx.mon_ptg_getAlpha.stop();
        }
    }

    static String hex(int v, int d) {
        String s = Integer.toHexString(v);
        while (s.length() < d) {
            s = "0" + s;
        }
        return s.substring(0, d);
    }
}
