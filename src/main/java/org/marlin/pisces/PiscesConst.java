/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 */
interface PiscesConst {
    /** enable JUL logger */
    static final boolean useJUL = false;

    /** enable development mode */
    static final boolean doDev = false;
    
    /** do checks */
    static final boolean doChecks = false;

    /* disable when algorithm / code is stable */
    static final boolean DO_AA_RANGE_CHECK = false; // TODO: always ensure byte[0;64]

    static final boolean USE_BINARY_SEARCH = true;
    static final int THRESHOLD_BINARY_SEARCH = 20;

    /** enable logs */
    static final boolean doLog = false;
    /** enable oversize logs */
    static final boolean doLogOverSize = false;
    /** enable traces */
    static final boolean doTrace = false;
    /** do statistics */
    static final boolean doStats = false;
    /** do monitors */
    static final boolean doMonitors = false;
    /** do flush monitors */
    static final boolean doFlushMonitors = true;
    /** use one polling thread to dump statistics/monitors */
    static final boolean useDumpThread = false;
    /** thread dump interval (ms) */
    static final long statDump = 5000L;
    /** do clean dirty array */
    static final boolean doCleanDirty = false;

    /** flag to use custom ceil() / floor() functions */
    static final boolean useFastMath = true;

    /** flag to enable logs related bounds checks */
    static final boolean doLogBounds = false;
    
    /* Initial Array sizing (initial context capacity) ~ 512K to 1 Mb */
    
    /** 2048 pixel (width x height) for initial capacity */
    static final int INITIAL_PIXEL_DIM    = PiscesRenderingEngine.getInitialImageSize();

    /* only odd numbers allowed below */
    static final int INITIAL_ARRAY        = 256;
    static final int INITIAL_MEDIUM_ARRAY = 4096; // large enough to avoid 99% array resizing
    static final int INITIAL_LARGE_ARRAY  = 8192; // large enough to avoid 99% array resizing
    static final int INITIAL_ARRAY_32K    = 32768;   // very large to avoid 99.99% array resizing
    static final int INITIAL_AA_ARRAY     = INITIAL_PIXEL_DIM; /* alpha row is twice larger then initial pixel dimensions */
}
