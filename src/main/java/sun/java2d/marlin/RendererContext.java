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

package sun.java2d.marlin;

import java.awt.geom.Path2D;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import static sun.java2d.marlin.ArrayCache.*;
import sun.java2d.marlin.MarlinRenderingEngine.NormalizingPathIterator;
import static sun.java2d.marlin.MarlinUtils.getCallerInfo;
import static sun.java2d.marlin.MarlinUtils.logInfo;

/**
 * This class is a renderer context dedicated to a single thread
 */
final class RendererContext implements MarlinConst {

    private static final String className = RendererContext.class.getName();
    // RendererContext creation counter
    private static final AtomicInteger contextCount = new AtomicInteger(1);
    // RendererContext statistics
    static final RendererStats stats = (doStats || doMonitors)
                                       ? RendererStats.createInstance(): null;

    private static final boolean USE_CACHE_HARD_REF = false;

    /**
     * Create a new renderer context
     *
     * @return new RendererContext instance
     */
    static RendererContext createContext() {
        final RendererContext newCtx = new RendererContext("ctx"
                    + Integer.toString(contextCount.getAndIncrement()));
        if (RendererContext.stats != null) {
            RendererContext.stats.allContexts.add(newCtx);
        }
        return newCtx;
    }

    // context name (debugging purposes)
    final String name;
    /*
     * Reference to this instance (hard, soft or weak).
     * @see MarlinRenderingEngine#REF_TYPE
     */
    final Object reference;
    // dynamic array caches kept using weak reference (low memory footprint)
    WeakReference<ArrayCachesHolder> refArrayCaches = null;
    // hard reference to array caches (for statistics)
    ArrayCachesHolder hardRefArrayCaches = null;
    // shared data
    final float[] float6 = new float[6];
    // shared curve (dirty) (Renderer / Stroker)
    final Curve curve = new Curve();
    // MarlinRenderingEngine.NormalizingPathIterator
    final NormalizingPathIterator npIterator;
    // MarlinRenderingEngine.TransformingPathConsumer2D
    final TransformingPathConsumer2D transformerPC2D;
    // recycled Path2D instance
    Path2D.Float p2d = null;
    final Renderer renderer;
    final Stroker stroker;
    // Simplifies out collinear lines
    final CollinearSimplifier simplifier = new CollinearSimplifier();
    final Dasher dasher;
    final MarlinTileGenerator ptg;
    final MarlinCache cache;

    /**
     * Constructor
     *
     * @param name
     */
    RendererContext(final String name) {
        if (logCreateContext) {
            MarlinUtils.logInfo("new RendererContext = " + name);
        }

        this.name = name;

        // MarlinRenderingEngine.NormalizingPathIterator:
        npIterator = new NormalizingPathIterator(this);

        // MarlinRenderingEngine.TransformingPathConsumer2D
        transformerPC2D = new TransformingPathConsumer2D();

        // Renderer:
        cache = new MarlinCache(this);
        renderer = new Renderer(this); // needs MarlinCache from rdrCtx.cache
        ptg = new MarlinTileGenerator(renderer);

        stroker = new Stroker(this);
        dasher = new Dasher(this);

        // Create the reference to this instance (hard, soft or weak):
        switch (MarlinRenderingEngine.REF_TYPE) {
            default:
            case MarlinRenderingEngine.REF_HARD:
                reference = this;
                break;
            case MarlinRenderingEngine.REF_SOFT:
                reference = new SoftReference<RendererContext>(this);
                break;
            case MarlinRenderingEngine.REF_WEAK:
                reference = new WeakReference<RendererContext>(this);
                break;
        }
    }

    // Array caches
    ArrayCachesHolder getArrayCachesHolder() {
        // Use hard reference first (cached resolved weak reference):
        ArrayCachesHolder holder = hardRefArrayCaches;
        if (holder == null) {
            // resolve reference:
            holder = (refArrayCaches != null)
                     ? refArrayCaches.get()
                     : null;
            // create a new ArrayCachesHolder if none is available
            if (holder == null) {
                if (logCreateContext) {
                    MarlinUtils.logInfo("new ArrayCachesHolder for "
                                        + "RendererContext = " + name);
                }

                holder = new ArrayCachesHolder();

                if (USE_CACHE_HARD_REF || doStats) {
                    // update hard reference:
                    hardRefArrayCaches = holder;
                }

                // update weak reference:
                refArrayCaches = new WeakReference<ArrayCachesHolder>(holder);
            }
        }
        return holder;
    }

    void resetArrayCachesHolder() {
        // keep hard reference to get cache statistics:
        if (!USE_CACHE_HARD_REF && !doStats) {
            hardRefArrayCaches = null;
        }
    }

    IntArrayCache getIntArrayCache(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return getArrayCachesHolder().intArrayCaches[bucket];
    }

    IntArrayCache getDirtyIntArrayCache(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return getArrayCachesHolder().dirtyIntArrayCaches[bucket];
    }

    FloatArrayCache getDirtyFloatArrayCache(final int length) {
        final int bucket = ArrayCache.getBucket(length);
        return getArrayCachesHolder().dirtyFloatArrayCaches[bucket];
    }

    ByteArrayCache getDirtyArrayCache(final int length) {
        final int bucket = ArrayCache.getBucketDirty(length);
        return getArrayCachesHolder().dirtyByteArrayCaches[bucket];
    }

    // dirty byte array cache
    byte[] getByteDirtyArray(final int length) {
        if (length <= MAX_DIRTY_BYTE_ARRAY_SIZE) {
            return getDirtyArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getByteDirtyArray[oversize]: length=\t" + length
                    + "\tfrom=\t" + getCallerInfo(className));
        }

        return new byte[length];
    }

    void putDirtyByteArray(final byte[] array) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_DIRTY_BYTE_ARRAY_SIZE)) {
            getDirtyArrayCache(length).putDirtyArray(array, length);
        }
    }

    byte[] widenDirtyByteArray(final byte[] in,
                               final int usedSize, final int newSize)
    {
        final int length = in.length;
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeDirtyByte++;
        }

        // maybe change bucket:
        // ensure getNewSize() > newSize:
        final byte[] res = getByteDirtyArray(getNewSize(usedSize));

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        // NO clean-up of array data = DIRTY ARRAY
        putDirtyByteArray(in);

        if (doLogWidenArray) {
            logInfo("widenDirtyArray byte[" + res.length + "]: usedSize=\t"
                    + usedSize + "\tlength=\t" + length + "\tnew length=\t"
                    + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    // int array cache
    int[] getIntArray(final int length) {
        if (length <= MAX_ARRAY_SIZE) {
            return getIntArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getIntArray[oversize]: length=\t" + length + "\tfrom=\t"
                    + getCallerInfo(className));
        }

        return new int[length];
    }

    // unused
    int[] widenIntArray(final int[] in, final int usedSize,
                        final int newSize, final int clearTo)
    {
        final int length = in.length;
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeInt++;
        }

        // maybe change bucket:
        // ensure getNewSize() > newSize:
        final int[] res = getIntArray(getNewSize(usedSize));

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        putIntArray(in, 0, clearTo); // ensure all array is cleared (grow-reduce algo)

        if (doLogWidenArray) {
            logInfo("widenArray int[" + res.length + "]: usedSize=\t"
                    + usedSize + "\tlength=\t" + length + "\tnew length=\t"
                    + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    void putIntArray(final int[] array, final int fromIndex,
                     final int toIndex)
    {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getIntArrayCache(length).putArray(array, length, fromIndex, toIndex);
        }
    }

    // dirty int array cache
    int[] getDirtyIntArray(final int length) {
        if (length <= MAX_ARRAY_SIZE) {
            return getDirtyIntArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getDirtyIntArray[oversize]: length=\t" + length
                    + "\tfrom=\t" + getCallerInfo(className));
        }

        return new int[length];
    }

    int[] widenDirtyIntArray(final int[] in,
                             final int usedSize, final int newSize)
    {
        final int length = in.length;
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeDirtyInt++;
        }

        // maybe change bucket:
        // ensure getNewSize() > newSize:
        final int[] res = getDirtyIntArray(getNewSize(usedSize));

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        // NO clean-up of array data = DIRTY ARRAY
        putDirtyIntArray(in);

        if (doLogWidenArray) {
            logInfo("widenDirtyArray int[" + res.length + "]: usedSize=\t"
                    + usedSize + "\tlength=\t" + length + "\tnew length=\t"
                    + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    void putDirtyIntArray(final int[] array) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getDirtyIntArrayCache(length).putDirtyArray(array, length);
        }
    }

    // dirty float array cache
    float[] getDirtyFloatArray(final int length) {
        if (length <= MAX_ARRAY_SIZE) {
            return getDirtyFloatArrayCache(length).getArray();
        }

        if (doStats) {
            oversize++;
        }

        if (doLogOverSize) {
            logInfo("getDirtyFloatArray[oversize]: length=\t" + length
                    + "\tfrom=\t" + getCallerInfo(className));
        }

        return new float[length];
    }

    float[] widenDirtyFloatArray(final float[] in,
                                 final int usedSize, final int newSize)
    {
        final int length = in.length;
        if (doChecks && length >= newSize) {
            return in;
        }
        if (doStats) {
            resizeDirtyFloat++;
        }

        // maybe change bucket:
        // ensure getNewSize() > newSize:
        final float[] res = getDirtyFloatArray(getNewSize(usedSize));

        System.arraycopy(in, 0, res, 0, usedSize); // copy only used elements

        // maybe return current array:
        // NO clean-up of array data = DIRTY ARRAY
        putDirtyFloatArray(in);

        if (doLogWidenArray) {
            logInfo("widenDirtyArray float[" + res.length + "]: usedSize=\t"
                    + usedSize + "\tlength=\t" + length + "\tnew length=\t"
                    + newSize + "\tfrom=\t" + getCallerInfo(className));
        }
        return res;
    }

    void putDirtyFloatArray(final float[] array) {
        final int length = array.length;
        if (((length & 0x1) == 0) && (length <= MAX_ARRAY_SIZE)) {
            getDirtyFloatArrayCache(length).putDirtyArray(array, length);
        }
    }

    final static class ArrayCachesHolder {
        // zero-filled int array cache:
        final IntArrayCache[] intArrayCaches;
        // dirty array caches:
        final IntArrayCache[] dirtyIntArrayCaches;
        final FloatArrayCache[] dirtyFloatArrayCaches;
        final ByteArrayCache[] dirtyByteArrayCaches;

        ArrayCachesHolder() {
            intArrayCaches = new IntArrayCache[BUCKETS];
            dirtyIntArrayCaches = new IntArrayCache[BUCKETS];
            dirtyFloatArrayCaches = new FloatArrayCache[BUCKETS];
            dirtyByteArrayCaches = new ByteArrayCache[BUCKETS];

            for (int i = 0; i < BUCKETS; i++) {
                intArrayCaches[i] = new IntArrayCache(ARRAY_SIZES[i]);
                // dirty array caches:
                dirtyIntArrayCaches[i] = new IntArrayCache(ARRAY_SIZES[i]);
                dirtyFloatArrayCaches[i] = new FloatArrayCache(ARRAY_SIZES[i]);
                dirtyByteArrayCaches[i] = new ByteArrayCache(DIRTY_BYTE_ARRAY_SIZES[i]);
            }
        }
    }
}
