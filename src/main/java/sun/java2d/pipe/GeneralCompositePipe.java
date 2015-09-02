/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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
package sun.java2d.pipe;

import java.awt.AlphaComposite;
import java.awt.CompositeContext;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import sun.awt.image.BufImgSurfaceData;
import sun.awt.image.IntegerInterleavedRaster;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.Blit;
import sun.java2d.loops.MaskBlit;
import sun.java2d.loops.CompositeType;

public class GeneralCompositePipe implements CompositePipe {

    /**
     * Per-thread TileContext (very small so do not use any Soft or Weak Reference)
     */
    private static final ThreadLocal<TileContext> tileContextThreadLocal = new ThreadLocal<TileContext>() {
        @Override
        protected TileContext initialValue() {
            return new TileContext();
        }
    };

    final static class TileContext {

        SunGraphics2D sunG2D;
        PaintContext paintCtxt;
        CompositeContext compCtxt;
        ColorModel compModel;
        Object pipeState;
        // LBO: cached values
        boolean isBlendComposite;
        int[] maskStride = new int[32];
        WritableRaster raster = null;

        TileContext() {
            // ThreadLocal constructor
        }

        void init(SunGraphics2D sg, PaintContext pCtx,
                CompositeContext cCtx, ColorModel cModel,
                boolean blendComposite) {
            sunG2D = sg;
            paintCtxt = pCtx;
            compCtxt = cCtx;
            compModel = cModel;
            isBlendComposite = blendComposite;
        }

        int[] getMaskStride(final int len) {
            int[] t = maskStride;
            if (t.length < len) {
                System.out.println("maskPixels = new int[" + len + "]");

                // create a larger stride and may free current maskStride (too small)
                maskStride = t = new int[len];
            }
            return t;
        }

        WritableRaster getDirtyWritableRaster(Raster in) {
            // buggy !!
            WritableRaster r = raster;
            if ((r == null)
                    || (raster.getClass() != in.getClass())
                    || (raster.getWidth() == in.getWidth())
                    || (raster.getHeight() == in.getHeight())) {
                raster = r = in.createCompatibleWritableRaster(
                        /*
                        Math.max(in.getWidth(), 32),
                        Math.max(in.getHeight(), 32)
                        */
                        );

                System.out.println("raster = " + raster);
            }
            return r;
        }
    }

    @Override
    public Object startSequence(SunGraphics2D sg, Shape s, Rectangle devR,
            int[] abox) {
        // warning: clone map:
        RenderingHints hints = sg.getRenderingHints();
        ColorModel model = sg.getDeviceColorModel();
        PaintContext paintContext =
                sg.paint.createContext(model, devR, s.getBounds2D(),
                sg.cloneTransform(),
                hints);
        CompositeContext compositeContext =
                sg.composite.createContext(paintContext.getColorModel(), model,
                hints);

        // BlendComposite matcher: classpath independent so use String.equals()
        boolean blendComposite = "sun.java2d.pipe.BlendComposite".equals(sg.composite.getClass().getName());

        // use ThreadLocal (to reduce memory footprint):
        final TileContext tc = tileContextThreadLocal.get();
        tc.init(sg, paintContext, compositeContext, model, blendComposite);
        return tc;
    }

    @Override
    public boolean needTile(Object ctx, int x, int y, int w, int h) {
        return true;
    }

    /**
     * GeneralCompositePipe.renderPathTile works with custom composite operator provided by an application
     */
    @Override
    public void renderPathTile(Object ctx,
            byte[] atile, int offset, int tilesize,
            int x, int y, int w, int h) {
        TileContext context = (TileContext) ctx;
        PaintContext paintCtxt = context.paintCtxt;
        CompositeContext compCtxt = context.compCtxt;
        SunGraphics2D sg = context.sunG2D;
        boolean blendComposite = context.isBlendComposite;

        Raster srcRaster = paintCtxt.getRaster(x, y, w, h);

        Raster dstRaster;
        Raster dstIn;
        WritableRaster dstOut;

        SurfaceData sd = sg.getSurfaceData();
        dstRaster = sd.getRaster(x, y, w, h);
        if (dstRaster instanceof WritableRaster && atile == null) {
            dstOut = (WritableRaster) dstRaster;
            dstOut = dstOut.createWritableChild(x, y, w, h, 0, 0, null);
            dstIn = dstOut;
        } else {
            dstIn = dstRaster.createChild(x, y, w, h, 0, 0, null);
            
            // TODO: cache such raster as it is very costly (int[])
            dstOut = dstIn.createCompatibleWritableRaster();
//            dstOut = context.getDirtyWritableRaster(dstIn);
        }

        if (blendComposite) {
            // define mask alpha into dstOut:

            // INT_RGBA only: TODO: check raster format !
            final int[] maskPixels = context.getMaskStride(w);

            // atile = null means mask=255 (src opacity full)
            if (atile == null) {
                for (int i = 0; i < w; i++) {
                    maskPixels[i] = 0xFF /*  << 24 */;
                }
                for (int j = 0; j < h; j++) {
                    // TODO: find most efficient method:
                    dstOut.setDataElements(0, j, w, 1, maskPixels);
                }
            } else {
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        maskPixels[i] = atile[ j * tilesize + (i + offset)] & 0xFF /*  << 24 */;
                    }
                    // TODO: find most efficient method:
                    dstOut.setDataElements(0, j, w, 1, maskPixels);
                }
            }
        }
        compCtxt.compose(srcRaster, dstIn, dstOut);

        if (dstRaster != dstOut && dstOut.getParent() != dstRaster) {
            if (dstRaster instanceof WritableRaster
                    && ((atile == null) || blendComposite)) {
                // TODO: find most efficient method to copy between rasters (use transfer type ?)
                ((WritableRaster) dstRaster).setDataElements(x, y, dstOut);
            } else {
                ColorModel cm = sg.getDeviceColorModel();
                BufferedImage resImg =
                        new BufferedImage(cm, dstOut,
                        cm.isAlphaPremultiplied(),
                        null);
                SurfaceData resData = BufImgSurfaceData.createData(resImg);
                if (atile == null) {
                    Blit blit = Blit.getFromCache(resData.getSurfaceType(),
                            CompositeType.SrcNoEa,
                            sd.getSurfaceType());
                    blit.Blit(resData, sd, AlphaComposite.Src, null,
                            0, 0, x, y, w, h);
                } else {
                    MaskBlit blit = MaskBlit.getFromCache(resData.getSurfaceType(),
                            CompositeType.SrcNoEa,
                            sd.getSurfaceType());
                    blit.MaskBlit(resData, sd, AlphaComposite.Src, null,
                            0, 0, x, y, w, h,
                            atile, offset, tilesize);
                }
            }
        }
    }

    @Override
    public void skipTile(Object ctx, int x, int y) {
    }

    @Override
    public void endSequence(Object ctx) {
        TileContext context = (TileContext) ctx;
        if (context.paintCtxt != null) {
            context.paintCtxt.dispose();
        }
        if (context.compCtxt != null) {
            context.compCtxt.dispose();
        }
    }
}