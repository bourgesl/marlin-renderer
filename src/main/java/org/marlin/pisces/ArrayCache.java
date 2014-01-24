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
package org.marlin.pisces;

import static org.marlin.pisces.PiscesUtils.logInfo;

import java.util.Timer;
import java.util.TimerTask;

/**
 *
 */
public final class ArrayCache implements PiscesConst {

    final static int BUCKETS = 4;
    final static int BUCKET_GROW = 4;
    final static int BUCKET_DIRTY_GROW = 2;
    final static int MIN_ARRAY_SIZE = 4096; // avoid too many resize (arrayCopy)
    /**
     * threshold to grow arrays by x4 or x2
     */
    final static int THRESHOLD_ARRAY_SIZE = 32 * 1024;
    // array sizes:
    final static int MAX_ARRAY_SIZE;
    final static int[] ARRAY_SIZES = new int[BUCKETS];
    // dirty array sizes:
    final static int MAX_DIRTY_ARRAY_SIZE;
    final static int[] DIRTY_ARRAY_SIZES = new int[BUCKETS];
    /* stats */
    private static Timer statTimer = null;
    /* stats */
    static int resizeInt = 0;
    static int resizeFloat = 0;
    static int resizeDirtyInt = 0;
    static int oversize = 0;

    static {
        /* initialize buckets for int/float arrays */
        int arraySize = MIN_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize *= BUCKET_GROW) {
            ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_ARRAY_SIZE = arraySize / BUCKET_GROW;

        /* initialize buckets for dirty int arrays (large AA chunk = 32 x pixels) */
        arraySize = BUCKET_DIRTY_GROW * PiscesCache.TILE_SIZE * INITIAL_PIXEL_DIM; 

        for (int i = 0; i < BUCKETS; i++, arraySize *= BUCKET_DIRTY_GROW) {
            DIRTY_ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("dirty arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_DIRTY_ARRAY_SIZE = arraySize / BUCKET_DIRTY_GROW;

        if (doStats || doMonitors) {
            logInfo("RenderingEngine.useThreadLocal = " + PiscesRenderingEngine.useThreadLocal);

            logInfo("ArrayCache.BUCKETS        = " + BUCKETS);
            logInfo("ArrayCache.MIN_ARRAY_SIZE = " + MIN_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_ARRAY_SIZE = " + MAX_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_DIRTY_ARRAY_SIZE = " + MAX_DIRTY_ARRAY_SIZE);

            // stats:
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    dumpStats();
                }
            });

            if (useDumpThread) {
                statTimer = new Timer("ArrayCache");
                statTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        dumpStats();
                    }
                }, statDump, statDump);
            }
        }
    }

    private ArrayCache() {
        // Utility class
    }

    public static void dumpStats() {
        if (doStats || doMonitors) {
            if (doStats) {
                if (resizeInt != 0 || resizeFloat != 0 || resizeDirtyInt != 0) {
                    logInfo("ArrayCache: int resize: " + resizeInt + " - float resize: " + resizeFloat
                            + " - dirty int resize: " + resizeDirtyInt + " - oversize: " + oversize);
                }
            }
            final RendererContext[] all = RendererContext.allContexts.toArray(new RendererContext[0]);
            for (RendererContext rdrCtx : all) {
                logInfo("RendererContext: " + rdrCtx.name);

                if (doMonitors) {
                    if (rdrCtx.mon_pre_getAATileGenerator.count != 0) {
                        logInfo(rdrCtx.mon_pre_getAATileGenerator.toString());
                    }
                    if (rdrCtx.mon_npi_currentSegment.count != 0) {
                        logInfo(rdrCtx.mon_npi_currentSegment.toString());
                    }
                    if (rdrCtx.mon_stroker_drawJoin.count != 0) {
                        logInfo(rdrCtx.mon_stroker_drawJoin.toString());
                    }
                    if (rdrCtx.mon_stroker_drawRoundCap.count != 0) {
                        logInfo(rdrCtx.mon_stroker_drawRoundCap.toString());
                    }
                    if (rdrCtx.mon_rdr_addLine.count != 0) {
                        logInfo(rdrCtx.mon_rdr_addLine.toString());
                    }
                    if (rdrCtx.mon_rdr_endRendering.count != 0) {
                        logInfo(rdrCtx.mon_rdr_endRendering.toString());
                    }
                    if (rdrCtx.mon_rdr_endRendering_Y.count != 0) {
                        logInfo(rdrCtx.mon_rdr_endRendering_Y.toString());
                    }
                    if (rdrCtx.mon_rdr_emitRow.count != 0) {
                        logInfo(rdrCtx.mon_rdr_emitRow.toString());
                    }
                    if (rdrCtx.mon_ptg_getAlpha.count != 0) {
                        logInfo(rdrCtx.mon_ptg_getAlpha.toString());
                    }
                    //
                    final long total = rdrCtx.mon_pre_getAATileGenerator.sum;
                    if (total != 0L) {
                        logInfo(rdrCtx.mon_pre_getAATileGenerator.name + " : " + ((100d * total) / total) + " %");

                        final long npiNorm = rdrCtx.mon_npi_currentSegment.sum;
                        logInfo(rdrCtx.mon_npi_currentSegment.name + " : " + ((100d * npiNorm) / total) + " %");

                        final long drawCap = rdrCtx.mon_stroker_drawRoundCap.sum;
                        logInfo(rdrCtx.mon_stroker_drawRoundCap.name + " : " + ((100d * drawCap) / total) + " %");

                        final long drawJoin = rdrCtx.mon_stroker_drawJoin.sum;
                        logInfo(rdrCtx.mon_stroker_drawJoin.name + " : " + ((100d * drawJoin) / total) + " %");

                        final long addLine = rdrCtx.mon_rdr_addLine.sum;
                        logInfo(rdrCtx.mon_rdr_addLine.name + " : " + ((100d * addLine) / total) + " %");

                        final long endRendering = rdrCtx.mon_rdr_endRendering.sum;
                        logInfo(rdrCtx.mon_rdr_endRendering.name + " : " + ((100d * endRendering) / total) + " %");

                        final long endRenderingY = rdrCtx.mon_rdr_endRendering_Y.sum;
                        logInfo(rdrCtx.mon_rdr_endRendering_Y.name + " : " + ((100d * endRenderingY) / total) + " %");

                        final long emitRow = rdrCtx.mon_rdr_emitRow.sum;
                        logInfo(rdrCtx.mon_rdr_emitRow.name + " : " + ((100d * emitRow) / total) + " %");

                        final long getAlpha = rdrCtx.mon_ptg_getAlpha.sum;
                        logInfo(rdrCtx.mon_ptg_getAlpha.name + " : " + ((100d * getAlpha) / total) + " %");
                    }
                    if (doFlushMonitors) {
                        rdrCtx.mon_pre_getAATileGenerator.reset();
                        rdrCtx.mon_npi_currentSegment.reset();
                        rdrCtx.mon_stroker_drawJoin.reset();
                        rdrCtx.mon_stroker_drawRoundCap.reset();
                        rdrCtx.mon_rdr_addLine.reset();
                        rdrCtx.mon_rdr_endRendering.reset();
                        rdrCtx.mon_rdr_endRendering_Y.reset();
                        rdrCtx.mon_rdr_emitRow.reset();
                        rdrCtx.mon_ptg_getAlpha.reset();
                    }
                }

                if (doStats) {
                    if (rdrCtx.stat_cache_rowAAChunk.count != 0) {
                        logInfo(rdrCtx.stat_cache_rowAAChunk.toString());
                        rdrCtx.stat_cache_rowAAChunk.reset();
                    }
                    if (rdrCtx.stat_cache_rowAARLE.count != 0) {
                        logInfo(rdrCtx.stat_cache_rowAARLE.toString());
                        rdrCtx.stat_cache_rowAARLE.reset();
                    }
                    if (rdrCtx.stat_rdr_poly_stack.count != 0) {
                        logInfo(rdrCtx.stat_rdr_poly_stack.toString());
                        rdrCtx.stat_rdr_poly_stack.reset();
                    }
                    if (rdrCtx.stat_rdr_edges.count != 0) {
                        logInfo(rdrCtx.stat_rdr_edges.toString());
                        rdrCtx.stat_rdr_edges.reset();
                    }
                    if (rdrCtx.stat_rdr_activeEdges.count != 0) {
                        logInfo(rdrCtx.stat_rdr_activeEdges.toString());
                        rdrCtx.stat_rdr_activeEdges.reset();
                    }
                    if (rdrCtx.stat_rdr_activeEdges_updates.count != 0) {
                        logInfo(rdrCtx.stat_rdr_activeEdges_updates.toString());
                        rdrCtx.stat_rdr_activeEdges_updates.reset();
                    }
                    if (rdrCtx.stat_rdr_activeEdges_no_adds.count != 0) {
                        logInfo(rdrCtx.stat_rdr_activeEdges_no_adds.toString());
                        rdrCtx.stat_rdr_activeEdges_no_adds.reset();
                    }
                    if (rdrCtx.stat_rdr_crossings_updates.count != 0) {
                        logInfo(rdrCtx.stat_rdr_crossings_updates.toString());
                        rdrCtx.stat_rdr_crossings_updates.reset();
                    }
                    if (rdrCtx.stat_rdr_crossings_sorts.count != 0) {
                        logInfo(rdrCtx.stat_rdr_crossings_sorts.toString());
                        rdrCtx.stat_rdr_crossings_sorts.reset();
                    }
                    if (rdrCtx.stat_rdr_crossings_bsearch.count != 0) {
                        logInfo(rdrCtx.stat_rdr_crossings_bsearch.toString());
                        rdrCtx.stat_rdr_crossings_bsearch.reset();
                    }

                    for (IntArrayCache cache : rdrCtx.intArrayCaches) {
                        cache.dumpStats();
                    }
                    for (FloatArrayCache cache : rdrCtx.floatArrayCaches) {
                        cache.dumpStats();
                    }
                    logInfo("Dirty ArrayCache for thread: " + rdrCtx.name);
                    for (IntArrayCache cache : rdrCtx.dirtyIntArrayCaches) {
                        cache.dumpStats();
                    }
                }
            }
        }
    }

    /* small methods used a lot (to be inlined / optimized by hotspot) */
    static int getBucket(final int length) {
        // Use size = (length / 2) * 2 => rounded to power of two
        // then switch == ? (unroll loops ?)
//        return length / MIN_ARRAY_SIZE
        for (int i = 0; i < BUCKETS; i++) {
            if (length <= ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }
    /* small methods used a lot (to be inlined / optimized by hotspot) */

    static int getBucketDirty(final int length) {
        // Use size = (length / 2) * 2 => rounded to power of two
        // then switch == ? (unroll loops ?)
//        return length / MIN_ARRAY_SIZE
        for (int i = 0; i < BUCKETS; i++) {
            if (length <= DIRTY_ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    static int getNewSize(final int newSize) {
        if (newSize < THRESHOLD_ARRAY_SIZE) {
            return BUCKET_GROW * newSize;
        }
        return 2 * newSize;
    }
}
