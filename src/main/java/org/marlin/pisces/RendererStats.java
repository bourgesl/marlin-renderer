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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.marlin.pisces.PiscesUtils.logInfo;
import org.marlin.pisces.stats.ArraySortDataCollection;
import org.marlin.pisces.stats.Histogram;
import org.marlin.pisces.stats.Monitor;
import org.marlin.pisces.stats.StatLong;

/**
 * This class gathers global rendering statistics for debugging purposes only
 */
public final class RendererStats implements PiscesConst {

    /** singleton */
    private static RendererStats singleton = null;

    final static RendererStats createInstance() {
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

    /* members */
    /** RendererContext collection as hard references (only used for debugging purposes) */
    final ConcurrentLinkedQueue<RendererContext> allContexts = new ConcurrentLinkedQueue<RendererContext>();
    /* timer */
    private final Timer statTimer;
    /* stats */
    final StatLong stat_cache_rowAA = new StatLong("cache.rowAA");
    final StatLong stat_cache_rowAAChunk = new StatLong("cache.rowAAChunk");
    final StatLong stat_rdr_poly_stack = new StatLong("renderer.poly.stack");
    final StatLong stat_rdr_curveBreak = new StatLong("renderer.curveBreakIntoLinesAndAdd");
    final StatLong stat_rdr_quadBreak = new StatLong("renderer.quadBreakIntoLinesAndAdd");
    final StatLong stat_rdr_edges = new StatLong("renderer.edges");
    final StatLong stat_rdr_edges_resizes = new StatLong("renderer.edges.resize");
    final StatLong stat_rdr_activeEdges = new StatLong("renderer.activeEdges");
    final StatLong stat_rdr_activeEdges_updates = new StatLong("renderer.activeEdges.updates");
    final StatLong stat_rdr_activeEdges_adds = new StatLong("renderer.activeEdges.adds");
    final StatLong stat_rdr_activeEdges_adds_high = new StatLong("renderer.activeEdges.adds_high");
    final StatLong stat_rdr_crossings_updates = new StatLong("renderer.crossings.updates");
    final StatLong stat_rdr_crossings_sorts = new StatLong("renderer.crossings.sorts");
    final StatLong stat_rdr_crossings_bsearch = new StatLong("renderer.crossings.bsearch");
    final StatLong stat_rdr_crossings_msorts = new StatLong("renderer.crossings.msorts");
    /* histograms */
    final Histogram hist_rdr_crossings = new Histogram("renderer.crossings");
    final Histogram hist_rdr_crossings_ratio = new Histogram("renderer.crossings.ratio");
    final Histogram hist_rdr_crossings_adds = new Histogram("renderer.crossings.adds");
    final Histogram hist_rdr_crossings_msorts = new Histogram("renderer.crossings.msorts");
    final Histogram hist_rdr_crossings_msorts_adds = new Histogram("renderer.crossings.msorts.adds");
    /* all stats */
    final StatLong[] statistics = new StatLong[]{
        stat_cache_rowAA,
        stat_cache_rowAAChunk,
        stat_rdr_poly_stack,
        stat_rdr_curveBreak,
        stat_rdr_quadBreak,
        stat_rdr_edges,
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
        hist_rdr_crossings_msorts_adds
    };
    /* monitors */
    final Monitor mon_pre_getAATileGenerator = new Monitor("PiscesRenderingEngine.getAATileGenerator()");
    final Monitor mon_npi_currentSegment = new Monitor("NormalizingPathIterator.currentSegment()");
    final Monitor mon_rdr_addLine = new Monitor("Renderer.addLine()");
    final Monitor mon_rdr_endRendering = new Monitor("Renderer.endRendering()");
    final Monitor mon_rdr_endRendering_Y = new Monitor("Renderer._endRendering(Y)");
    final Monitor mon_rdr_emitRow = new Monitor("Renderer.emitRow()");
    final Monitor mon_ptg_getAlpha = new Monitor("PiscesTileGenerator.getAlpha()");
    /* all monitors */
    final Monitor[] monitors = new Monitor[]{
        mon_pre_getAATileGenerator,
        mon_npi_currentSegment,
        mon_rdr_addLine,
        mon_rdr_endRendering,
        mon_rdr_endRendering_Y,
        mon_rdr_emitRow,
        mon_ptg_getAlpha
    };
    /* array data */
    final ArraySortDataCollection adc = new ArraySortDataCollection();

    private RendererStats() {
        super();

        /* stats */
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                dump();
                // dump array data:
                ArraySortDataCollection.save("/tmp/ArraySortDataCollection.ser", adc);
            }
        });

        if (useDumpThread) {
            statTimer = new Timer("RendererStats");
            statTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    dump();
                }
            }, statDump, statDump);
        } else {
            statTimer = null;
        }
    }

    void dump() {
        if (doStats) {
            ArrayCache.dumpStats();
        }
        final RendererContext[] all = allContexts.toArray(new RendererContext[0]);
        for (RendererContext rdrCtx : all) {
            logInfo("RendererContext: " + rdrCtx.name);

            if (doMonitors) {
                for (Monitor monitor : monitors) {
                    if (monitor.count != 0) {
                        logInfo(monitor.toString());
                    }
                }
                // As getAATileGenerator percents:
                final long total = mon_pre_getAATileGenerator.sum;
                if (total != 0L) {
                    for (Monitor monitor : monitors) {
                        logInfo(monitor.name + " : " + ((100d * monitor.sum) / total) + " %");
                    }
                }
                if (doFlushMonitors) {
                    for (Monitor m : monitors) {
                        m.reset();
                    }
                }
            }

            if (doStats) {
                for (StatLong stat : statistics) {
                    if (stat.count != 0) {
                        logInfo(stat.toString());
                        stat.reset();
                    }
                }
                // IntArrayCaches stats:
                for (IntArrayCache cache : rdrCtx.intArrayCaches) {
                    cache.dumpStats();
                }
                // FloatArrayCaches stats:
                for (FloatArrayCache cache : rdrCtx.floatArrayCaches) {
                    cache.dumpStats();
                }
                // DirtyArrayCaches stats:
                logInfo("Dirty ArrayCache for thread: " + rdrCtx.name);
                for (ByteArrayCache cache : rdrCtx.dirtyArrayCaches) {
                    cache.dumpStats();
                }
            }
        }
    }
}
