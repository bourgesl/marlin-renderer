/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 */package org.marlin.pipe;

import java.awt.Color;
import org.marlin.ReentrantContext;
import org.marlin.pipe.BlendComposite.BlendingContext;
import static org.marlin.pipe.MarlinCompositor.BLEND_QUALITY;
import static org.marlin.pipe.MarlinCompositor.BLEND_SPEED;
import static org.marlin.pipe.MarlinCompositor.USE_OLD_BLENDER;
import sun.java2d.SunGraphics2D;
import static sun.java2d.SunGraphics2D.PAINT_ALPHACOLOR;
import sun.java2d.loops.SurfaceType;
import static org.marlin.pipe.MarlinCompositor.BLEND_SPEED_COLOR;

final class CompositorContext extends ReentrantContext {

    /*
    // Per-thread TileState (~1K very small so do not use any Weak Reference)
    private static final ReentrantContextProvider<CompositorContext> TILE_CTX_PROVIDER
                                                                     = new ReentrantContextProviderTL<CompositorContext>(
                    ReentrantContextProvider.REF_HARD) {
        @Override
        protected CompositorContext newContext() {

            System.out.println("new CompositorContext");
            return new CompositorContext();
        }
    };
     */
    // members
    private final BlendComposite composite = new BlendComposite();
    private final GammaCompositePipe.TileContext gcp_tile_ctx = new GammaCompositePipe.TileContext();
    // lazy initialized blenders:
    private BlendingContext bcInt = null;
    private BlendingContext bcIntCol = null;
    private BlendingContext bcByte = null;

    CompositorContext() {
        // ThreadLocal constructor
    }

    void dispose() {
        // TODO !
    }

    BlendComposite getComposite() {
        return composite;
    }

    GammaCompositePipe.TileContext getGammaCompositePipeTileContext() {
        return gcp_tile_ctx;
    }

    BlendComposite.BlendingContext init(final BlendComposite composite, final SurfaceType sdt,
                                        final SunGraphics2D sg) {

        if ((sdt == SurfaceType.IntArgb) || (sdt == SurfaceType.IntArgbPre)) {
            if (BLEND_SPEED_COLOR && (sg.paintState <= PAINT_ALPHACOLOR)) {
                if (bcIntCol == null) {
                    bcIntCol = new BlendingContextIntARGBFastColor();
                }
                return bcIntCol.init(composite, sg);
            }
            if (bcInt == null) {
                bcInt = (USE_OLD_BLENDER) ? new BlendingContextIntSRGB()
                        : ((BLEND_SPEED) ? new BlendingContextIntARGBFast()
                                : ((BLEND_QUALITY) ? new BlendingContextIntARGBExact()
                                        : new BlendingContextIntARGB()));
            }
            return bcInt.init(composite, sg);
        } else if ((sdt == SurfaceType.FourByteAbgr) || (sdt == SurfaceType.FourByteAbgrPre)) {
            if (bcByte == null) {
                bcByte = new BlendingContextByteABGR();
            }
            return bcByte.init(composite, sg);
        }
        return null;
    }
}
