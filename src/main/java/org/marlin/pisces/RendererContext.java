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

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.marlin.pisces.ArrayCache.*;
import org.marlin.pisces.PiscesRenderingEngine.NormalizingPathIterator;
import static org.marlin.pisces.PiscesUtils.getCallerInfo;
import static org.marlin.pisces.PiscesUtils.logInfo;

/**
 * This class is a renderer context dedicated to a single thread (using thread local)
 */
final class RendererContext implements PiscesConst {

    private static final String className = RendererContext.class.getName();
    /** context created counter */
    private static final AtomicInteger contextCount = new AtomicInteger(1);
    // TODO: use weak references instead of hard references (only used for debugging purposes)
    static final ConcurrentLinkedQueue<RendererContext> allContexts = (doStats || doMonitors) ? new ConcurrentLinkedQueue<RendererContext>() : null;

    /**
     * Create a new renderer context
     *
     * @return new RendererContext instance
     */
    static RendererContext createContext() {
        final RendererContext newCtx = new RendererContext("ctx" + Integer.toString(contextCount.getAndIncrement()));
        if (doStats || doMonitors) {
            allContexts.add(newCtx);
        }
        return newCtx;
    }

    /* members */
    /** context name (debugging purposes) */
    final String name;

    /**
     * Reference to this instance (hard, soft or weak). 
     * @see PiscesRenderingEngine#REF_TYPE
     */
    final Object reference;

    /** dynamic array caches */
    final IntArrayCache[] intArrayCaches = new IntArrayCache[BUCKETS];
    final FloatArrayCache[] floatArrayCaches = new FloatArrayCache[BUCKETS];
    /* dirty instances (use them carefully) */
    /** dynamic DIRTY byte array for PiscesCache */
    final ByteArrayCache[] dirtyArrayCaches = new ByteArrayCache[BUCKETS];
    /* shared data */
    /* fixed arrays (dirty) */
    final float[] float6 = new float[6];
    /** shared curve (dirty) (Renderer / Stroker) */
    final Curve curve = new Curve();
    /* pisces class instances */
    /** PiscesRenderingEngine.NormalizingPathIterator */
    final NormalizingPathIterator npIterator;
    /* Renderer */
    final Renderer renderer;
    /** Stroker */
    final Stroker stroker;
    /* Simplifies out collinear lines */
    final CollinearSimplifier simplifier = new CollinearSimplifier();
    /** Dasher */
    final Dasher dasher;
    /**PiscesTileGenerator */
    final PiscesTileGenerator ptg;
    /** PiscesCache */
    final PiscesCache piscesCache;
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
    final StatLong stat_rdr_activeEdges_no_adds = new StatLong("renderer.activeEdges.noadds");
    final StatLong stat_rdr_crossings_updates = new StatLong("renderer.crossings.updates");
    final StatLong stat_rdr_crossings_sorts = new StatLong("renderer.crossings.sorts");
    final StatLong stat_rdr_crossings_bsearch = new StatLong("renderer.crossings.bsearch");
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
        stat_rdr_activeEdges_no_adds,
        stat_rdr_crossings_updates,
        stat_rdr_crossings_sorts,
        stat_rdr_crossings_bsearch
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

    /**
     * Constructor
     *
     * @param name
     */
    RendererContext(final String name) {
        if (logCreateContext) {
            PiscesUtils.logInfo("new RendererContext = " + name);
        }

        this.name = name;

        for (int i = 0; i < BUCKETS; i++) {
            intArrayCaches[i] = new IntArrayCache(ARRAY_SIZES[i]);
            floatArrayCaches[i] = new FloatArrayCache(ARRAY_SIZES[i]);
            // dirty:
            dirtyArrayCaches[i] = new ByteArrayCache(DIRTY_ARRAY_SIZES[i]);
        }

        // PiscesRenderingEngine.NormalizingPathIterator:
        npIterator = new NormalizingPathIterator(this);

        // Renderer:
        piscesCache = new PiscesCache(this);
        renderer = new Renderer(this); // needs piscesCache
        ptg = new PiscesTileGenerator(renderer);

        stroker = new Stroker(this);
        dasher = new Dasher(this);

        // Create the reference to this instance (hard, soft or weak):
        switch (PiscesRenderingEngine.REF_TYPE) {
            default:
            case PiscesRenderingEngine.REF_HARD:
                reference = this;
                break;
            case PiscesRenderingEngine.REF_SOFT:
                reference = new SoftReference<RendererContext>(this);
                break;
            case PiscesRenderingEngine.REF_WEAK:
                reference = new WeakReference<RendererContext>(this);
                break;
        }
    }

    /* Array caches */
    IntArrayCache getIntArrayCache(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return intArrayCaches[bucket];
    }

    FloatArrayCache getFloatArrayCache(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return floatArrayCaches[bucket];
    }

    ByteArrayCache getDirtyArrayCache(final int length) {
        final int bucket = ArrayCache.getBucketDirty(length);
        return dirtyArrayCaches[bucket];
    }

    /* dirty piscesCache */
    byte[] getDirtyArray(final int length) {
        if (length <= MAX_DIRTY_ARRAY_SIZE) {
            return getDirtyArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getDirtyIntArray[oversize]: length=\t" + length + "\tfrom=\t" + getCallerInfo(className));
        }

        return new byte[length];
    }

    void putDirtyArray(final byte[] array) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_DIRTY_ARRAY_SIZE)) {
            getDirtyArrayCache(length).putDirtyArray(array, length);
        }
    }

    /* TODO: replace with new signature */
    byte[] widenDirtyArray(final byte[] in, final int cursize, final int numToAdd) {
        final int length = in.length;
        final int newSize = cursize + numToAdd;
        if (length >= newSize) {
            return in;
        }

        final byte[] res = RendererContext.this.widenDirtyArray(in, length, cursize, newSize);

        if (doLog) {
            logInfo("widenDirtyArray int[" + res.length + "]: cursize=\t" + cursize + "\tlength=\t" + length
                    + "\tnew length=\t" + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    private byte[] widenDirtyArray(final byte[] in, final int length, final int usedSize, final int newSize) {
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeDirty++;
        }

        // maybe change bucket:
        final byte[] res = getDirtyArray(2 * newSize); // Use x2

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        putDirtyArray(in); // NO clear array data = DIRTY ARRAY ie manual clean when getting an array!!

        return res;
    }

    /* int array piscesCache */
    int[] getIntArray(final int length) {
        if (length <= MAX_ARRAY_SIZE) {
            return getIntArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getIntArray[oversize]: length=\t" + length + "\tfrom=\t" + getCallerInfo(className));
        }

        return new int[length];
    }

    /* TODO: replace with new signature */
    int[] widenArray(final int[] in, final int length, final int usedSize, final int newSize, final int clearTo) {
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeInt++;
        }

        // maybe change bucket:
        final int[] res = getIntArray(getNewSize(newSize)); // Use GROW or x2

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        putIntArray(in, 0, clearTo); // ensure all array is cleared (grow-reduce algo)

        return res;
    }

    int[] widenArrayPartially(final int[] in, final int length, final int fromIndex, final int toIndex, final int newSize) {
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeInt++;
        }

        // maybe change bucket:
        final int[] res = getIntArray(getNewSize(newSize)); // Use GROW or x2

        System.arraycopy(in, fromIndex, res, fromIndex, toIndex - fromIndex); // copy only used elements

        // maybe return current array:
        putIntArray(in, fromIndex, toIndex); // only partially cleared

        return res;
    }

    void putIntArray(final int[] array, final int fromIndex, final int toIndex) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getIntArrayCache(length).putArray(array, length, fromIndex, toIndex);
        }
    }

    /* float array piscesCache */
    float[] getFloatArray(final int length) {
        if (length <= MAX_ARRAY_SIZE) {
            return getFloatArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        // TODO: use very big one at last !
        if (doLogOverSize) {
            logInfo("getFloatArray[oversize]: length=\t" + length + "\tfrom=\t" + getCallerInfo(className));
        }

        return new float[length];
    }

    /* TODO: replace with new signature */
    float[] widenArray(final float[] in, final int length, final int usedSize, final int newSize, final int clearTo) {
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeFloat++;
        }

        // maybe change bucket:
        final float[] res = getFloatArray(getNewSize(newSize)); // Use GROW or x2

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        putFloatArray(in, 0, clearTo); // ensure all array is cleared (grow-reduce algo)

        return res;
    }

    void putFloatArray(final float[] array, final int fromIndex, final int toIndex) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getFloatArrayCache(length).putArray(array, length, fromIndex, toIndex);
        }
    }

    /* stats */
    static class StatLong {

        public final String name;
        public long count;
        public long sum;
        public long min;
        public long max;

        StatLong(String name) {
            this.name = name;
            reset();
        }

        final void reset() {
            count = 0;
            sum = 0;
            min = Integer.MAX_VALUE;
            max = Integer.MIN_VALUE;
        }

        final void add(int val) {
            count++;
            sum += val;
            if (val < min) {
                min = val;
            }
            if (val > max) {
                max = val;
            }
        }

        final void add(long val) {
            count++;
            sum += val;
            if (val < min) {
                min = val;
            }
            if (val > max) {
                max = val;
            }
        }

        @Override
        public final String toString() {
            return name + '[' + count + "] sum: " + sum + " avg: " + (((double) sum) / count) + " [" + min + " | " + max + "]";
        }
    }

    /* monitors */
    static final class Monitor extends StatLong {

        private final static long INVALID = -1L;
        private long start = INVALID;

        Monitor(String name) {
            super(name);
        }

        void start() {
            start = System.nanoTime();
        }

        void stop() {
            final long elapsed = System.nanoTime() - start;
            if (start != INVALID && elapsed > 0l) {
                add(elapsed);
            }
            start = INVALID;
        }
    }
}
