/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.marlin.pisces.MarlinUtils.logInfo;
import org.marlin.pisces.stats.Histogram;
import org.marlin.pisces.stats.Monitor;
import org.marlin.pisces.stats.StatLong;

/**
 * This class gathers global rendering statistics for debugging purposes only
 */
public final class RendererStats implements MarlinConst {

    // singleton
    private static volatile RendererStats singleton = null;

    static RendererStats getInstance() {
        if (singleton == null) {
            singleton = new RendererStats();
        }
        return singleton;
    }

    public static void dumpStats() {
        if (singleton != null) {
            singleton.dump();
        }
    }

    /* RendererContext collection as hard references
       (only used for debugging purposes) */
    protected final ConcurrentLinkedQueue<RendererContext> allContexts
        = new ConcurrentLinkedQueue<RendererContext>();
    // stats
    protected final StatLong stat_cache_rowAA
        = new StatLong("cache.rowAA");
    protected final StatLong stat_cache_rowAAChunk
        = new StatLong("cache.rowAAChunk");
    protected final StatLong stat_cache_tiles
        = new StatLong("cache.tiles");
    protected final StatLong stat_rdr_poly_stack_curves
        = new StatLong("renderer.poly.stack.curves");
    protected final StatLong stat_rdr_poly_stack_types
        = new StatLong("renderer.poly.stack.types");
    protected final StatLong stat_rdr_addLine
        = new StatLong("renderer.addLine");
    protected final StatLong stat_rdr_addLine_skip
        = new StatLong("renderer.addLine.skip");
    protected final StatLong stat_rdr_curveBreak
        = new StatLong("renderer.curveBreakIntoLinesAndAdd");
    protected final StatLong stat_rdr_curveBreak_dec
        = new StatLong("renderer.curveBreakIntoLinesAndAdd.dec");
    protected final StatLong stat_rdr_curveBreak_inc
        = new StatLong("renderer.curveBreakIntoLinesAndAdd.inc");
    protected final StatLong stat_rdr_quadBreak
        = new StatLong("renderer.quadBreakIntoLinesAndAdd");
    protected final StatLong stat_rdr_quadBreak_dec
        = new StatLong("renderer.quadBreakIntoLinesAndAdd.dec");
    protected final StatLong stat_rdr_edges
        = new StatLong("renderer.edges");
    protected final StatLong stat_rdr_edges_count
        = new StatLong("renderer.edges.count");
    protected final StatLong stat_rdr_edges_resizes
        = new StatLong("renderer.edges.resize");
    protected final StatLong stat_rdr_activeEdges
        = new StatLong("renderer.activeEdges");
    protected final StatLong stat_rdr_activeEdges_updates
        = new StatLong("renderer.activeEdges.updates");
    protected final StatLong stat_rdr_activeEdges_adds
        = new StatLong("renderer.activeEdges.adds");
    protected final StatLong stat_rdr_activeEdges_adds_high
        = new StatLong("renderer.activeEdges.adds_high");
    protected final StatLong stat_rdr_crossings_updates
        = new StatLong("renderer.crossings.updates");
    protected final StatLong stat_rdr_crossings_sorts
        = new StatLong("renderer.crossings.sorts");
    protected final StatLong stat_rdr_crossings_bsearch
        = new StatLong("renderer.crossings.bsearch");
    protected final StatLong stat_rdr_crossings_msorts
        = new StatLong("renderer.crossings.msorts");
    // growable arrays
    protected final StatLong stat_array_dasher_firstSegmentsBuffer
        = new StatLong("array.dasher.firstSegmentsBuffer.d_float");
    protected final StatLong stat_array_stroker_polystack_curves
        = new StatLong("array.stroker.polystack.curves.d_float");
    protected final StatLong stat_array_stroker_polystack_curveTypes
        = new StatLong("array.stroker.polystack.curveTypes.d_byte");
    protected final StatLong stat_array_marlincache_rowAAChunk
        = new StatLong("array.marlincache.rowAAChunk.d_byte");
    protected final StatLong stat_array_marlincache_touchedTile
        = new StatLong("array.marlincache.touchedTile.int");
    protected final StatLong stat_array_renderer_alphaline
        = new StatLong("array.renderer.alphaline.int");
    protected final StatLong stat_array_renderer_crossings
        = new StatLong("array.renderer.crossings.int");
    protected final StatLong stat_array_renderer_aux_crossings
        = new StatLong("array.renderer.aux_crossings.int");
    protected final StatLong stat_array_renderer_edgeBuckets
        = new StatLong("array.renderer.edgeBuckets.int");
    protected final StatLong stat_array_renderer_edgeBucketCounts
        = new StatLong("array.renderer.edgeBucketCounts.int");
    protected final StatLong stat_array_renderer_edgePtrs
        = new StatLong("array.renderer.edgePtrs.int");
    protected final StatLong stat_array_renderer_aux_edgePtrs
        = new StatLong("array.renderer.aux_edgePtrs.int");
    // histograms
    protected final Histogram hist_rdr_crossings
        = new Histogram("renderer.crossings");
    protected final Histogram hist_rdr_crossings_ratio
        = new Histogram("renderer.crossings.ratio");
    protected final Histogram hist_rdr_crossings_adds
        = new Histogram("renderer.crossings.adds");
    protected final Histogram hist_rdr_crossings_msorts
        = new Histogram("renderer.crossings.msorts");
    protected final Histogram hist_rdr_crossings_msorts_adds
        = new Histogram("renderer.crossings.msorts.adds");
    protected final Histogram hist_tile_generator_alpha
        = new Histogram("tile_generator.alpha");
    // all stats
    protected final StatLong[] statistics = new StatLong[]{
        stat_cache_rowAA,
        stat_cache_rowAAChunk,
        stat_cache_tiles,
        stat_rdr_poly_stack_types,
        stat_rdr_poly_stack_curves,
        stat_rdr_addLine,
        stat_rdr_addLine_skip,
        stat_rdr_curveBreak,
        stat_rdr_curveBreak_dec,
        stat_rdr_curveBreak_inc,
        stat_rdr_quadBreak,
        stat_rdr_quadBreak_dec,
        stat_rdr_edges,
        stat_rdr_edges_count,
        stat_rdr_edges_resizes,
        stat_rdr_activeEdges,
        stat_rdr_activeEdges_updates,
        stat_rdr_activeEdges_adds,
        stat_rdr_activeEdges_adds_high,
        stat_rdr_crossings_updates,
        stat_rdr_crossings_sorts,
        stat_rdr_crossings_bsearch,
        stat_rdr_crossings_msorts,
        hist_rdr_crossings,
        hist_rdr_crossings_ratio,
        hist_rdr_crossings_adds,
        hist_rdr_crossings_msorts,
        hist_rdr_crossings_msorts_adds,
        hist_tile_generator_alpha,
        stat_array_dasher_firstSegmentsBuffer,
        stat_array_stroker_polystack_curves,
        stat_array_stroker_polystack_curveTypes,
        stat_array_marlincache_rowAAChunk,
        stat_array_marlincache_touchedTile,
        stat_array_renderer_alphaline,
        stat_array_renderer_crossings,
        stat_array_renderer_aux_crossings,
        stat_array_renderer_edgeBuckets,
        stat_array_renderer_edgeBucketCounts,
        stat_array_renderer_edgePtrs,
        stat_array_renderer_aux_edgePtrs
    };
    // monitors
    protected final Monitor mon_pre_getAATileGenerator
        = new Monitor("MarlinRenderingEngine.getAATileGenerator()");
    protected final Monitor mon_npi_currentSegment
        = new Monitor("NormalizingPathIterator.currentSegment()");
    protected final Monitor mon_rdr_addLine
        = new Monitor("Renderer.addLine()");
    protected final Monitor mon_rdr_endRendering
        = new Monitor("Renderer.endRendering()");
    protected final Monitor mon_rdr_endRendering_Y
        = new Monitor("Renderer._endRendering(Y)");
    protected final Monitor mon_rdr_copyAARow
        = new Monitor("Renderer.copyAARow()");
    protected final Monitor mon_pipe_renderTiles
        = new Monitor("AAShapePipe.renderTiles()");
    protected final Monitor mon_ptg_getAlpha
        = new Monitor("MarlinTileGenerator.getAlpha()");
    protected final Monitor mon_debug
        = new Monitor("DEBUG()");
    // all monitors
    protected final Monitor[] monitors = new Monitor[]{
        mon_pre_getAATileGenerator,
        mon_npi_currentSegment,
        mon_rdr_addLine,
        mon_rdr_endRendering,
        mon_rdr_endRendering_Y,
        mon_rdr_copyAARow,
        mon_pipe_renderTiles,
        mon_ptg_getAlpha,
        mon_debug
    };

    private RendererStats() {
        super();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                dump();
            }
        });

        if (USE_DUMP_THREAD) {
            final Timer statTimer = new Timer("RendererStats");
            statTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    dump();
                }
            }, STAT_DUMP, STAT_DUMP);
        }
    }

    void dump() {
        if (DO_STATS) {
            ArrayCache.dumpStats();
        }
        final RendererContext[] all = allContexts.toArray(
                                          new RendererContext[allContexts.size()]);
        for (RendererContext rdrCtx : all) {
            logInfo("RendererContext: " + rdrCtx.name);

            if (DO_MONITORS) {
                for (Monitor monitor : monitors) {
                    if (monitor.count != 0) {
                        logInfo(monitor.toString());
                    }
                }
                // As getAATileGenerator percents:
                final long total = mon_pre_getAATileGenerator.sum;
                if (total != 0L) {
                    for (Monitor monitor : monitors) {
                        logInfo(monitor.name + " : "
                                + ((100d * monitor.sum) / total) + " %");
                    }
                }
                if (DO_FLUSH_MONITORS) {
                    for (Monitor m : monitors) {
                        m.reset();
                    }
                }
            }

            if (DO_STATS) {
                for (StatLong stat : statistics) {
                    if (stat.count != 0) {
                        logInfo(stat.toString());
                        stat.reset();
                    }
                }
                // IntArrayCaches stats:
                final RendererContext.ArrayCachesHolder holder
                    = rdrCtx.getArrayCachesHolder();

                logInfo("Array caches for thread: " + rdrCtx.name);

                for (IntArrayCache cache : holder.intArrayCaches) {
                    cache.dumpStats();
                }

                logInfo("Dirty Array caches for thread: " + rdrCtx.name);

                for (IntArrayCache cache : holder.dirtyIntArrayCaches) {
                    cache.dumpStats();
                }
                for (FloatArrayCache cache : holder.dirtyFloatArrayCaches) {
                    cache.dumpStats();
                }
                for (ByteArrayCache cache : holder.dirtyByteArrayCaches) {
                    cache.dumpStats();
                }
            }
        }
    }
}
