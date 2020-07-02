/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.marlin.pipe;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Vector;
import org.marlin.pisces.MarlinUtils;
import sun.misc.Unsafe;

/**
 *
 */
final class OffHeapArray {

    private final static boolean LOG_UNSAFE_MALLOC = false;

    public static final int CACHE_LINE_SIZE = Integer.getInteger("offheap.align", 64);
    public static final int PAGE_SIZE;

    // unsafe reference
    static final Unsafe UNSAFE;
    // size of int / float
    static final int SIZE_INT;
    // offset of int[]
    static final long OFF_BYTE;
    // offset of int[]
    static final long OFF_INT;

    static {
        UNSAFE = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
            @Override
            public Unsafe run() {
                Unsafe ref = null;
                try {
                    final Field field = Unsafe.class.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    ref = (Unsafe) field.get(null);
                } catch (Exception e) {
                    throw new InternalError("Unable to get sun.misc.Unsafe instance", e);
                }
                return ref;
            }
        });

        PAGE_SIZE = UNSAFE.pageSize();

//        System.out.println("CACHE_LINE_SIZE: " + CACHE_LINE_SIZE);
//        System.out.println("PAGE_SIZE: " + PAGE_SIZE);

        SIZE_INT = 4; // jdk 1.6 (Unsafe.ARRAY_INT_INDEX_SCALE)

        OFF_BYTE = Unsafe.ARRAY_BYTE_BASE_OFFSET; // 16
        OFF_INT = Unsafe.ARRAY_INT_BASE_OFFSET; // 12

        // Mimics Java2D Disposer:
        AccessController.doPrivileged(new PrivilegedAction<Void>() {

            @Override
            public Void run() {
                /*
                 * The thread must be a member of a thread group
                 * which will not get GCed before VM exit.
                 * Make its parent the top-level thread group.
                 */
                final Thread t = new Thread(
                        MarlinUtils.getRootThreadGroup(),
                        new OffHeapDisposer(),
                        "MarlinCompositor Disposer");
                t.setContextClassLoader(null);
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY - 2);
                t.start();
                return null;
            }
        });
    }

    /* members */
    long address;
    long length;
    long start;

    private final static long PADDING = CACHE_LINE_SIZE;

    OffHeapArray(final Object parent, final long len) {
        // note: may throw OOME:
        this.address = UNSAFE.allocateMemory(len + PADDING);
        this.length = len;
        final long mod = (address % PADDING);
        //System.out.println("Modulo address : "+mod);
        this.start = this.address + ((mod == 0L) ? 0L : (PADDING - mod));
        /*
        System.out.println("Modulo start: "+(start % PADDING) + " PADDING = "+PADDING
                + " cache aligned: "+isCacheAligned(start)
                + " page aligned: "+isPageAligned(start)
        );
        */

        if (LOG_UNSAFE_MALLOC) {
            MarlinUtils.logInfo(System.currentTimeMillis()
                    + ": OffHeapArray.allocateMemory =   "
                    + len + " to addr = " + this.address
                    + " start at " + start
            );
        }

        // Create the phantom reference to ensure freeing off-heap memory:
        REF_LIST.add(new OffHeapReference(parent, this));
    }

    public static boolean isPageAligned(long address) {
        return isAligned(address, PAGE_SIZE);
    }

    public static boolean isCacheAligned(final long address) {
        return isAligned(address, CACHE_LINE_SIZE);
    }

    public static boolean isAligned(final long address, final long align) {
        if (Long.bitCount(align) != 1) {
            throw new IllegalArgumentException("Alignment must be a power of 2");
        }
        return (address & (align - 1)) == 0;
    }

    void free() {
        UNSAFE.freeMemory(this.address);
        if (LOG_UNSAFE_MALLOC) {
            MarlinUtils.logInfo(System.currentTimeMillis()
                    + ": OffHeapArray.freeMemory =       "
                    + this.length
                    + " at addr = " + this.address);
        }
        this.address = 0L;
    }

    void fill(final byte val) {
        UNSAFE.setMemory(this.address, this.length, val);
    }

    // Custom disposer (replaced by jdk9 Cleaner)
    // Parent reference queue
    private static final ReferenceQueue<Object> REF_QUEUE
                                                = new ReferenceQueue<Object>();
    // reference list
    private static final Vector<OffHeapReference> REF_LIST
                                                  = new Vector<OffHeapReference>(32);

    static final class OffHeapReference extends PhantomReference<Object> {

        private final OffHeapArray array;

        OffHeapReference(final Object parent, final OffHeapArray edges) {
            super(parent, REF_QUEUE);
            this.array = edges;
        }

        void dispose() {
            // free off-heap blocks
            this.array.free();
        }
    }

    static final class OffHeapDisposer implements Runnable {

        @Override
        public void run() {
            final Thread currentThread = Thread.currentThread();
            OffHeapReference ref;

            // check interrupted:
            for (; !currentThread.isInterrupted();) {
                try {
                    ref = (OffHeapReference) REF_QUEUE.remove();
                    ref.dispose();

                    REF_LIST.remove(ref);

                } catch (InterruptedException ie) {
                    MarlinUtils.logException("OffHeapDisposer interrupted:",
                            ie);
                }
            }
        }
    }
}
