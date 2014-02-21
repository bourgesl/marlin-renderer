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

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.marlin.pisces.ArrayCache.*;
import org.marlin.pisces.PiscesRenderingEngine.NormalizingPathIterator;
import static org.marlin.pisces.PiscesUtils.getCallerInfo;
import static org.marlin.pisces.PiscesUtils.logInfo;

/**
 * This class is a renderer context dedicated to a single thread (using thread local)
 *
 * Memory usage: int[] sun.java2d.pisces.Stroker.<init>(RendererContext)	32792	1
 * sun.java2d.pisces.Stroker$PolyStack.<init>(RendererContext) sun.java2d.pisces.Renderer.<init>(RendererContext)	303248
 * 6	Filtered out RendererContext.<init>(String) sun.java2d.pisces.PiscesCache.<init>(RendererContext)	1048	1	Filtered
 * out RendererContext.<init>(String) float[] sun.java2d.pisces.PiscesRenderingEngine.getAATileGenerator(...) 1416	59
 * sun.java2d.pisces.Renderer.<init>(RendererContext)	131096	1
 * sun.java2d.pisces.Stroker$PolyStack.<init>(RendererContext)	32792	1 RendererContext.<init>(String)	2176	3
 * RendererContext.<init>(String)	256	5 RendererContext.createContext()	40	1
 *
 * Total: 472072 bytes from initial arrays only ~ 512K for 1 RendererContext
 *
 */
final class RendererContext implements PiscesConst {

    private static final String className = RendererContext.class.getName();
    /**
     * context created counter
     */
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
    /**
     * context name (debugging purposes)
     */
    final String name;

    /**
     * Soft reference to this instance
     */
    final SoftReference<RendererContext> softRef;

    /**
     * dynamic array caches
     */
    final IntArrayCache[] intArrayCaches = new IntArrayCache[BUCKETS];
    final FloatArrayCache[] floatArrayCaches = new FloatArrayCache[BUCKETS];
    /* dirty instances (use them carefully) */
    /**
     * dynamic DIRTY int array piscesCache
     */
    final IntArrayCache[] dirtyIntArrayCaches = new IntArrayCache[BUCKETS];
    /* shared data */
    /* fixed arrays (dirty) */
    final float[] float6 = new float[6];
    /**
     * shared curve (dirty) (Renderer / Stroker)
     */
    final Curve curve = new Curve();
    /* pisces class instances */
    /**
     * PiscesRenderingEngine.NormalizingPathIterator
     */
    final NormalizingPathIterator npIterator;
    /* Renderer */
    final Renderer renderer;
    /* Stroker */
    final Stroker stroker;
    /**
     * Dasher
     */
    final Dasher dasher;
    /**
     * PiscesTileGenerator
     */
    final PiscesTileGenerator ptg;
    /**
     * PiscesCache
     */
    final PiscesCache piscesCache;
    /* stats */
    final StatInteger stat_cache_rowAA = new StatInteger("cache.rowAA");
    final StatInteger stat_cache_rowAAChunk = new StatInteger("cache.rowAAChunk");
    final StatInteger stat_rdr_poly_stack = new StatInteger("renderer.poly.stack");
    final StatInteger stat_rdr_edges = new StatInteger("renderer.edges");
    final StatInteger stat_rdr_edges_resizes = new StatInteger("renderer.edges.resize");
    final StatInteger stat_rdr_activeEdges = new StatInteger("renderer.activeEdges");
    final StatInteger stat_rdr_activeEdges_updates = new StatInteger("renderer.activeEdges.updates");
    final StatInteger stat_rdr_activeEdges_no_adds = new StatInteger("renderer.activeEdges.noadds");
    final StatInteger stat_rdr_crossings_updates = new StatInteger("renderer.crossings.updates");
    final StatInteger stat_rdr_crossings_sorts = new StatInteger("renderer.crossings.sorts");
    final StatInteger stat_rdr_crossings_bsearch = new StatInteger("renderer.crossings.bsearch");
    /* monitors */
    final Monitor mon_pre_getAATileGenerator = new Monitor("PiscesRenderingEngine.getAATileGenerator()");
    final Monitor mon_npi_currentSegment = new Monitor("NormalizingPathIterator.currentSegment()");
    final Monitor mon_rdr_addLine = new Monitor("Renderer.addLine()");
    final Monitor mon_rdr_endRendering = new Monitor("Renderer.endRendering()");
    final Monitor mon_rdr_endRendering_Y = new Monitor("Renderer._endRendering(Y)");
    final Monitor mon_rdr_emitRow = new Monitor("Renderer.emitRow()");
    final Monitor mon_ptg_getAlpha = new Monitor("PiscesTileGenerator.getAlpha()");

    /**
     * Constructor
     *
     * @param name
     */
    RendererContext(final String name) {
//        System.out.println("createContext = " + name);

        this.name = name;

        for (int i = 0; i < BUCKETS; i++) {
            intArrayCaches[i] = new IntArrayCache(ARRAY_SIZES[i]);
            floatArrayCaches[i] = new FloatArrayCache(ARRAY_SIZES[i]);
            // dirty:
            dirtyIntArrayCaches[i] = new IntArrayCache(DIRTY_ARRAY_SIZES[i]);
        }

        // PiscesRenderingEngine.NormalizingPathIterator:
        npIterator = new NormalizingPathIterator(this);

        // Renderer:
        piscesCache = new PiscesCache(this);
        renderer = new Renderer(this); // needs piscesCache
        ptg = new PiscesTileGenerator(renderer);

        stroker = new Stroker(this);
        dasher = new Dasher(this);

        // Create the soft reference to this instance:
        this.softRef = new SoftReference<RendererContext>(this);
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

    IntArrayCache getDirtyIntArrayCache(final int length) {
        final int bucket = ArrayCache.getBucketDirty(length);
        return dirtyIntArrayCaches[bucket];
    }

    /* dirty piscesCache */
    int[] getDirtyIntArray(final int length) {
        if (length <= MAX_DIRTY_ARRAY_SIZE) {
            return getDirtyIntArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getDirtyIntArrayCache[oversize]: length=\t" + length + "\tfrom=\t" + getCallerInfo(className));
        }

        return new int[length];
    }

    void putDirtyIntArray(final int[] array) {
        final int length = array.length;
        if (length <= MAX_DIRTY_ARRAY_SIZE) {
            getDirtyIntArrayCache(length).putDirtyArray(array, length);
        }
    }

    int[] widenDirtyIntArray(final int[] in, final int cursize, final int numToAdd) {

        final int length = in.length;
        final int newSize = cursize + numToAdd;
        if (length >= newSize) {
            return in;
        }

        final int[] res = _widenDirtyArray(in, length, cursize, newSize);

        if (doLog) {
            logInfo("widenDirtyArray int[" + res.length + "]: cursize=\t" + cursize + "\tlength=\t" + length
                    + "\tnew length=\t" + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    private int[] _widenDirtyArray(final int[] in, final int length, final int usedSize, final int newSize) {
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeDirtyInt++;
        }

        // maybe change bucket:
        final int[] res = getDirtyIntArray(2 * newSize); // Use x2

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        putDirtyIntArray(in); // NO clear array data = DIRTY ARRAY ie manual clean when getting an array!!

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
        putIntArray(in, clearTo); // ensure all array is cleared (grow-reduce algo)

        return res;
    }

    void putIntArray(final int[] array, final int usedSize) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getIntArrayCache(length).putArray(array, length, usedSize);
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
        putFloatArray(in, clearTo); // ensure all array is cleared (grow-reduce algo)

        return res;
    }

    void putFloatArray(final float[] array, final int usedSize) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getFloatArrayCache(length).putArray(array, length, usedSize);
        }
    }

    /* stats */
    static class StatInteger {

        public final String name;
        public long count;
        public long sum;
        public long min;
        public long max;

        StatInteger(String name) {
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
    static final class Monitor extends StatInteger {

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
