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

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import static org.marlin.pipe.BlendComposite.GAMMA_LUT;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.NORM_GAMMA;
import static org.marlin.pipe.BlendComposite.TILE_WIDTH;

final class BlendingContextIntARGB extends BlendComposite.BlendingContext {

    private final static int NUM_COMP = 4;

    /* members */
    private int _extraAlpha;
    private BlendComposite.Blender _blender;

    // recycled arrays into context (shared):
    private final int[] _c_srcPixel = new int[NUM_COMP];
    private final int[] _srcPixel = new int[NUM_COMP];
    private final int[] _dstPixel = new int[NUM_COMP];
    private final int[] _result = new int[NUM_COMP];
    // horiz arrays:
    private int[] _srcPixels = new int[TILE_WIDTH];
    private int[] _dstPixels = new int[TILE_WIDTH];

    BlendingContextIntARGB() {
        super();
    }

    BlendComposite.BlendingContext init(final BlendComposite composite) {
        this._blender = BlendComposite.Blender.getBlenderFor(composite);
        this._extraAlpha = Math.round(127f * composite.extraAlpha); // [0; 127] ie 7 bits
        return this; // fluent API
    }

    int[] getSrcPixels(final int len) {
        int[] t = _srcPixels;
        if (t.length < len) {
            // System.out.println("RESIZE _srcPixels to " + len);
            // create a larger stride and may free current maskStride (too small)
            _srcPixels = t = new int[len];
        }
        return t;
    }

    int[] getDstPixels(final int len) {
        int[] t = _dstPixels;
        if (t.length < len) {
            // System.out.println("RESIZE _dstPixels to " + len);
            // create a larger stride and may free current maskStride (too small)
            _dstPixels = t = new int[len];
        }
        return t;
    }

    void compose(final int srcRGBA, final Raster srcIn,
                 final byte[] atile, final int offset, final int tilesize,
                 final WritableRaster dstOut,
                 final int w, final int h) {

        /*
             System.out.println("srcIn = " + srcIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int[] gamma_dir = GAMMA_LUT.dir;
        final int[] gamma_inv = GAMMA_LUT.inv;

        final int extraAlpha = this._extraAlpha; // 7 bits

//            final BlendComposite.Blender blender = _blender;
        // use shared arrays:
        final int[] c_srcPixel = _c_srcPixel;
        final int[] srcPixel = _srcPixel;
        final int[] dstPixel = _dstPixel;
        final int[] result = _result;

        final int[] srcPixels = (srcIn != null) ? getSrcPixels(w) : null;
        final int[] dstPixels = getDstPixels(w);

        int pixel;

        // Prepare source pixel if constant in tile:
        if (srcIn == null) {
            // Source pixel Linear RGBA:
            pixel = srcRGBA;

            // Linear RGBA components are not pre-multiplied by alpha:
            // NOP
            // Gamma-correction on Linear RGBA: 
            // color components in range [0; 32767]
            c_srcPixel[0] = gamma_dir[(pixel >> 16) & NORM_BYTE];
            c_srcPixel[1] = gamma_dir[(pixel >> 8) & NORM_BYTE];
            c_srcPixel[2] = gamma_dir[(pixel) & NORM_BYTE];
            c_srcPixel[3] = (pixel >> 24) & NORM_BYTE;

            // c_srcPixel is Gamma-corrected Linear RGBA.
        }

        int am, alpha, fs, fd;
        int offTile;

        for (int y = 0; y < h; y++) {
            // TODO: use directly DataBufferInt (offsets + stride)
            if (srcIn != null) {
                srcIn.getDataElements(0, y, w, 1, srcPixels);
            }
            dstOut.getDataElements(0, y, w, 1, dstPixels);

            offTile = y * tilesize + offset;

            for (int x = 0; x < w; x++) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
                am = (atile != null) ? atile[offTile + x] & NORM_BYTE : NORM_BYTE;

                /*
                     * coverage = 0 means translucent: 
                     * result = destination (already the case)
                 */
                if (am != 0) {
                    // ELSE: coverage between [1;255]:
                    // Apply extra alpha:
                    // alpha in range [0; 32385] (15bits)
                    am *= extraAlpha; // TODO: out of loop if no tile !

                    if (am == NORM_ALPHA) {
                        // mask with full opacity
                        // output = source OVER (totally)
                        // Source pixel Linear RGBA:
                        dstPixels[x] = (srcIn != null) ? srcPixels[x] : srcRGBA;
                        continue;
                    }

                    if (srcIn == null) {
                        // Copy prepared source pixel:
                        srcPixel[0] = c_srcPixel[0];
                        srcPixel[1] = c_srcPixel[1];
                        srcPixel[2] = c_srcPixel[2];
                        srcPixel[3] = c_srcPixel[3];
                    } else {
                        // Source pixel Linear RGBA:
                        pixel = srcPixels[x];

                        // Linear RGBA components are not pre-multiplied by alpha:
                        // NOP
                        // Gamma-correction on Linear RGBA: 
                        // color components in range [0; 32767]
                        srcPixel[0] = gamma_dir[(pixel >> 16) & NORM_BYTE];
                        srcPixel[1] = gamma_dir[(pixel >> 8) & NORM_BYTE];
                        srcPixel[2] = gamma_dir[(pixel) & NORM_BYTE];
                        srcPixel[3] = (pixel >> 24) & NORM_BYTE;
                    }
                    // srcPixel is Gamma-corrected Linear RGBA.

                    // fade operator:
                    // Rs = As x Coverage
                    // alpha in range [0; 32385] (15bits)
                    srcPixel[3] = (srcPixel[3] * am) / NORM_BYTE;
                    /*                        
                        if (srcPixel[3] > NORM_ALPHA || srcPixel[3] < 0) {
                            System.out.println("srcPixel[3] overflow");
                        }
                     */
                    // Destination pixel:
                    {
                        // Dest pixel Linear RGBA:
                        pixel = dstPixels[x];

                        // Linear RGBA components are not pre-multiplied by alpha:
                        // NOP
                        // Gamma-correction on Linear RGBA: 
                        // color components in range [0; 32767]
                        dstPixel[0] = gamma_dir[(pixel >> 16) & NORM_BYTE];
                        dstPixel[1] = gamma_dir[(pixel >> 8) & NORM_BYTE];
                        dstPixel[2] = gamma_dir[(pixel) & NORM_BYTE];
                        dstPixel[3] = (pixel >> 24) & NORM_BYTE;

                        // alpha in range [0; 32385] (15bits)
                        dstPixel[3] *= NORM_BYTE7;
                    }
                    // dstPixel is Gamma-corrected Linear RGBA.

                    // Ported-Duff rules in action:
                    // R = S x fs + D x fd
                    // Src Over Dst rule:
                    // fs = Sa
                    // fd = Da x (1 - Sa)
                    // Factors in range [0; 32385] (15bits)
                    fs = (srcPixel[3]);
                    fd = (dstPixel[3] * (NORM_ALPHA - fs)) / NORM_ALPHA;
                    /*
                        if (fs > NORM_ALPHA || fs < 0) {
                            System.out.println("fs overflow");
                        }
                        if (fd > NORM_ALPHA || fd < 0) {
                            System.out.println("fd overflow");
                        }
                     */
 /*
                        System.out.println("fs: " + fs);
                        System.out.println("fd: " + fd);

                        System.out.println("srcPixel: " + Arrays.toString(srcPixel));
                        System.out.println("dstPixel: " + Arrays.toString(dstPixel));
                     */
//                            blender.blend(srcPixel, dstPixel, fs, fd, result);
                    // ALPHA in range [0; 32385] (15bits):
                    alpha = fs + fd;

                    if (alpha == 0) {
                        // output = none
                        // Source pixel Linear RGBA:
                        dstPixels[x] = 0;
                        continue;
                    }

                    // color components in range [0; 32767]
                    // no overflow: 15b + 15b < 31b
                    result[0] = (srcPixel[0] * fs + dstPixel[0] * fd) / alpha;
                    result[1] = (srcPixel[1] * fs + dstPixel[1] * fd) / alpha;
                    result[2] = (srcPixel[2] * fs + dstPixel[2] * fd) / alpha;
                    // alpha in range [0; 255]
                    result[3] = alpha / NORM_BYTE7;

                    // System.out.println("result: " + Arrays.toString(result));
                    // Faster with explicit bound checks !
                    if (result[0] > NORM_GAMMA || result[1] > NORM_GAMMA || result[2] > NORM_GAMMA
                            || result[3] > NORM_BYTE) {
                        // System.out.println("overflow");
                        result[0] = NORM_GAMMA;
                        result[1] = NORM_GAMMA;
                        result[2] = NORM_GAMMA;
                        result[3] = NORM_BYTE;
                    }
                    if (result[0] < 0 || result[1] < 0 || result[2] < 0 || result[3] < 0) {
                        // System.out.println("underflow");
                        result[0] = 0;
                        result[1] = 0;
                        result[2] = 0;
                        result[3] = 0;
                    }

                    // result is Gamma-corrected Linear RGBA.
                    // Inverse Gamma-correction on Linear RGBA: 
                    // color components in range [0; 32767]
                    pixel = (result[3] << 24)
                            | (gamma_inv[result[0]]) << 16
                            | (gamma_inv[result[1]]) << 8
                            | (gamma_inv[result[2]]);

                    // Linear RGBA components are not pre-multiplied by alpha:
                    // NOP
                    dstPixels[x] = pixel;
                }
            }
            dstOut.setDataElements(0, y, w, 1, dstPixels);
        }
    }

    public void composeKO(final int srcRGBA, final Raster srcIn,
                          final byte[] atile, final int offset, final int tilesize,
                          final WritableRaster dstOut,
                          final int w, final int h) {

        /*
             System.out.println("src = " + src.getBounds());
             System.out.println("dstIn = " + dstIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int[] gamma_dir = GAMMA_LUT.dir;
        final int[] gamma_inv = GAMMA_LUT.inv;

        final int extraAlpha = this._extraAlpha;

        final BlendComposite.Blender blender = _blender;

        final DataBuffer srcBuffer = (srcIn != null) ? srcIn.getDataBuffer() : null;
        final DataBuffer dstBuffer = (DataBufferInt) dstOut.getDataBuffer();

        // use shared arrays:
        final int[] srcPixel = _srcPixel;
        final int[] dstPixel = _dstPixel;
        final int[] result = _result;

//            final int[] srcPixels = (srcIn != null) ? getSrcPixels(w) : null;
        //final int[] dstPixels = getDstPixels(w);
        int pixel, am, as, ad, ar, fs, fd;
        int offTile, offBuf;

        for (int y = 0; y < h; y++) {
            // TODO: use directly BufferInt
/*                
                if (srcIn != null) {
                    srcIn.getDataElements(0, y, w, 1, srcPixels);
                }
                dstOut.getDataElements(0, y, w, 1, dstPixels);
             */
// dstOut.getDataElements(0, y, w, 1, dstPixels);

            offBuf = y * w;
            offTile = y * tilesize + offset;

            for (int x = 0; x < w; x++) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
                am = (atile == null) ? NORM_BYTE
                        : atile[offTile + x] & NORM_BYTE;

                /*
                     * coverage = 0 means translucent: 
                     * result = destination (already the case)
                 */
                if (am != 0) {
                    // ELSE: coverage between [1;255]:
                    am *= extraAlpha;

                    pixel = (srcIn != null) ? srcBuffer.getElem(offBuf + x) /*srcPixels[x]*/ : srcRGBA;

                    if (am == NORM_ALPHA) {
                        // mask with full opacity
                        // output = source
                        dstBuffer.setElem(offBuf + x, pixel);
//                            dstPixels[x] = pixel;
                    } else {
                        // TODO: cache last computation (rgba src + rgba dst + mask alpha) => (same result)
                        // blend:
                        as = ((pixel >> 24) & NORM_BYTE);

                        // fade operator:
                        // alpha in range [0; 255]
                        as = (as * am) / NORM_ALPHA;

                        // note: skip check as != 0 (low probability)
                        // RGBA: premultiply color component by alpha:
                        // color components in range [0; 32767]
                        srcPixel[0] = gamma_dir[(pixel >> 16) & NORM_BYTE];
                        srcPixel[1] = gamma_dir[(pixel >> 8) & NORM_BYTE];
                        srcPixel[2] = gamma_dir[(pixel) & NORM_BYTE];
                        srcPixel[3] = as;

                        if (as != NORM_BYTE) {
                            // Premultiply
                            srcPixel[0] = (srcPixel[0] * as) / NORM_BYTE;
                            srcPixel[1] = (srcPixel[1] * as) / NORM_BYTE;
                            srcPixel[2] = (srcPixel[2] * as) / NORM_BYTE;
                        }

                        pixel = dstBuffer.getElem(offBuf + x);
//                            pixel = dstPixels[x];
                        ad = (pixel >> 24) & NORM_BYTE;

                        // note: skip check ad != 0 (low probability)
                        // RGBA: premultiply color component by alpha:
                        // color components in range [0; 32767]
                        dstPixel[0] = gamma_dir[(pixel >> 16) & NORM_BYTE];
                        dstPixel[1] = gamma_dir[(pixel >> 8) & NORM_BYTE];
                        dstPixel[2] = gamma_dir[(pixel) & NORM_BYTE];
                        dstPixel[3] = ad;

                        if (ad != NORM_BYTE) {
                            // Premultiply
                            dstPixel[0] = (dstPixel[0] * ad) / NORM_BYTE;
                            dstPixel[1] = (dstPixel[1] * ad) / NORM_BYTE;
                            dstPixel[2] = (dstPixel[2] * ad) / NORM_BYTE;
                        }

                        // Src Over Dst rule: factors in range [0; 255]
                        fs = NORM_BYTE;
                        fd = NORM_BYTE - as;

                        // recycle int[] instances:
                        blender.blend(srcPixel, dstPixel, fs, fd, result);

                        // mixes the result with the opacity
                        // alpha in range [0; 255]
                        ar = result[3];

                        if (ar == 0) {
                            dstBuffer.setElem(offBuf + x, 0);
//                                dstPixels[x] = 0x00FFFFFF;
                        } else {
                            // RGBA: divide color component by alpha 
                            // color components in range [0; 32767]
//                                dstPixels[x] = 
                            dstBuffer.setElem(offBuf + x,
                                    (ar << 24)
                                    | (gamma_inv[result[0]] / ar) << 16
                                    | (gamma_inv[result[1]] / ar) << 8
                                    | (gamma_inv[result[2]] / ar)
                            );
                        }
                    }
                }
            }
//                dstOut.setDataElements(0, y, w, 1, dstPixels);
        }
    }
}
