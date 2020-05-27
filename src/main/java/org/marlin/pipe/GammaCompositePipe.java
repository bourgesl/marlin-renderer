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
 */
package org.marlin.pipe;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.CompositePipe;

public final class GammaCompositePipe implements CompositePipe {

    public final static GammaCompositePipe INSTANCE = new GammaCompositePipe();

    @Override
    public Object startSequence(final SunGraphics2D sg, final Shape s, final Rectangle devR, final int[] abox) {
        // no-op
        return null;
    }

    public Object startSequence(final CompositorContext ctx, final SunGraphics2D sg, final Shape s, final Rectangle devR, final int[] abox) {

        final SurfaceData sd = sg.getSurfaceData();
        final SurfaceType sdt = sd.getSurfaceType();

        // Already checked supported types (basic surfaces, not volatile or accelerated surfaces):
        if (false && !MarlinCompositor.isSurfaceSupported(sdt)) {
            return null; // means invalid pipe
        }

        BlendComposite blendComposite = null;

        if (sg.composite instanceof AlphaComposite) {
            final AlphaComposite ac = (AlphaComposite) sg.composite;

            if (ac.getRule() == AlphaComposite.SRC_OVER) {
                // only SrcOver implemented for now
                // TODO: implement all Porter-Duff rules 
                // set (optional) extra alpha:
                blendComposite = BlendComposite.getInstance(BlendComposite.BlendingMode.SRC_OVER, ac.getAlpha());
            }
        }

        if (blendComposite == null) {
            // System.out.println("Unsupported blending mode for composite: " + sg.composite);
            return null; // means invalid pipe
        }

        final BlendComposite.BlendingContext compositeContext = ctx.init(blendComposite, sdt);
        if (compositeContext == null) {
            // System.out.println("Unable to create BlendingContext");
            return null; // means invalid pipe
        }

        final int colorRGBA;
        final PaintContext paintContext;

        if (sg.paint instanceof Color) {
            colorRGBA = ((Color) sg.paint).getRGB();
            paintContext = null;
        } else {
            colorRGBA = 0;
            // warning: clone hints map:
            paintContext = sg.paint.createContext(sg.getDeviceColorModel(), devR, s.getBounds2D(),
                    sg.cloneTransform(), sg.getRenderingHints());
        }

        // use ThreadLocal (to reduce memory footprint):
        final TileContext tc = ctx.getGammaCompositePipeTileContext();
        tc.init(sd, colorRGBA, paintContext, compositeContext, blendComposite);
        return tc;
    }

    @Override
    public boolean needTile(final Object ctx, final int x, final int y, final int w, final int h) {
        return true;
    }

    /**
     * GeneralCompositePipe.renderPathTile works with custom composite operator provided by an application
     */
    @Override
    public void renderPathTile(final Object ctx,
                               final byte[] atile, final int offset, final int tilesize,
                               final int x, final int y, final int w, final int h) {

        // System.out.println("render tile: (" + w + " x " + h + ")");
        final TileContext context = (TileContext) ctx;
        final BlendComposite.BlendingContext compCtxt = context.compCtxt;

        int rgba = 0;
        final PaintContext paintCtxt = context.paintCtxt;
        final Raster srcRaster;

        if (paintCtxt != null) {
            // // hack PaintContext -> cached tile is limited to 64x64 !
            srcRaster = paintCtxt.getRaster(x, y, w, h);
        } else {
            // hack ColorPaintContext -> to avoid fill color on complete tile (cached tile is limited to 64x64):
            rgba = context.colorRGBA;
            srcRaster = null;
        }

        final Raster dstRaster = context.sd.getRaster(x, y, w, h);

        if (!(dstRaster instanceof WritableRaster)) {
            throw new IllegalStateException("Raster is not writable [" + dstRaster + "]");
        }

        // System.out.println("createWritableChild: (" + w + " x " + h + ")");
        final WritableRaster dstOut;

        if (MarlinCompositor.USE_OLD_BLENDER) {
            dstOut = (WritableRaster) dstRaster;
        } else {
            dstOut = ((WritableRaster) dstRaster).createWritableChild(x, y, w, h, 0, 0, null);
        }

        // Perform compositing:
        // srcRaster = paint raster
        // dstIn = surface destination raster (input)
        // dstOut = writable destination raster (output)
        compCtxt.compose(rgba, srcRaster, dstOut,
                x, y, w, h,
                atile, offset, tilesize);
    }

    @Override
    public void skipTile(final Object ctx, final int x, final int y) {
    }

    @Override
    public void endSequence(final Object ctx) {
        ((TileContext) ctx).dispose();
    }

    final static class TileContext {

        int colorRGBA;
        PaintContext paintCtxt;
        BlendComposite.BlendingContext compCtxt;
        BlendComposite blendComposite = null;
        SurfaceData sd = null;

        TileContext() {
            // ThreadLocal constructor
        }

        void init(final SurfaceData sd,
                  final int colorRGBA, final PaintContext pCtx,
                  final BlendComposite.BlendingContext cCtx,
                  final BlendComposite blendComposite) {

            this.colorRGBA = colorRGBA;
            this.paintCtxt = pCtx;
            this.compCtxt = cCtx;
            this.blendComposite = blendComposite;
            this.sd = sd;
        }

        void dispose() {
            this.colorRGBA = 0;
            if (paintCtxt != null) {
                paintCtxt.dispose();
                paintCtxt = null;
            }
            compCtxt = null;
            blendComposite = null;
            sd = null;
        }
    }
}
