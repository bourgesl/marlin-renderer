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

import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import static org.marlin.pipe.BlendComposite.CONTRAST;
import static org.marlin.pipe.BlendComposite.FIX_CONTRAST;
import static org.marlin.pipe.BlendComposite.FIX_LUM;
import static org.marlin.pipe.BlendComposite.GAMMA_LUT;
import static org.marlin.pipe.BlendComposite.MASK_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.TILE_WIDTH;
import static org.marlin.pipe.BlendComposite.USE_Y_TO_L;
import static org.marlin.pipe.BlendComposite.Y2L_LUT;
import static org.marlin.pipe.BlendComposite.brightness_LUT;
import static org.marlin.pipe.BlendComposite.luminance;

final class BlendingContextIntARGBExact extends BlendComposite.BlendingContext {

    // horiz arrays:
    private int[] _srcPixels = new int[TILE_WIDTH];
    private int[] _dstPixels = new int[TILE_WIDTH];

    BlendingContextIntARGBExact() {
        super();
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

    @Override
    void compose(final int srcRGBA, final Raster srcIn,
                 final WritableRaster dstOut,
                 final int x, final int y, final int w, final int h,
                 final byte[] mask, final int maskoff, final int maskscan) {

        /*
             System.out.println("srcIn = " + srcIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int[] gamma_dir = GAMMA_LUT.dir;
        final int[] gamma_dirL = GAMMA_LUT.dirL;
        final int[] gamma_inv = GAMMA_LUT.inv;

        final int[] bright_dir = (FIX_LUM) ? brightness_LUT.dir : null;
        final int[] bright_inv = (FIX_LUM) ? brightness_LUT.inv : null;

        final int[] y2l_dir = (USE_Y_TO_L) ? Y2L_LUT.dirL : gamma_dirL;

        final int extraAlpha = this._extraAlpha; // 7 bits

//            final BlendComposite.Blender blender = _blender;
        // use shared arrays:
        final int[] srcPixels = (srcIn != null) ? getSrcPixels(w) : null;
        final int[] dstPixels = getDstPixels(w);

        int pixel;
        int csr, csg, csb, csa;
        int sr, sg, sb, sa;
        int dr, dg, db, da;
        int rr, rg, rb, ra;

        int ls = 0, lls = 0;

        // Prepare source pixel if constant in tile:
        if (srcIn == null) {
            // Source pixel Linear RGBA:
            pixel = srcRGBA;

            // Linear RGBA components are not pre-multiplied by alpha:
            // NOP
            // Gamma-correction on Linear RGBA: 
            // color components in range [0; 32385]
            csr = gamma_dir[(pixel >> 16) & NORM_BYTE];
            csg = gamma_dir[(pixel >> 8) & NORM_BYTE];
            csb = gamma_dir[(pixel) & NORM_BYTE];
            csa = (pixel >> 24) & NORM_BYTE;

            // c_srcPixel is Gamma-corrected Linear RGBA.
            if (FIX_CONTRAST) {
                ls = luminance(csr, csg, csb); // Y
                lls = y2l_dir[ls]; // linear
            }
        } else {
            csr = 0;
            csg = 0;
            csb = 0;
            csa = 0;
        }

        final int contrast_scale = 4 * CONTRAST;
        int am, alpha, fs, fd;
        int offTile;

        int ld, lld, ll_old, lro, lr;
        int contrast;

        for (int j = 0; j < h; j++) {
            // TODO: use directly DataBufferInt (offsets + stride)
            if (srcIn != null) {
                srcIn.getDataElements(0, j, w, 1, srcPixels);
            }
            dstOut.getDataElements(0, j, w, 1, dstPixels);

            offTile = j * maskscan + maskoff;

            for (int i = 0; i < w; i++) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
                am = (mask != null) ? mask[offTile + i] & NORM_BYTE : NORM_BYTE;

                /*
                 * coverage = 0 means translucent: 
                 * result = destination (already the case)
                 */
                if (am == 0) {
                    continue;
                }
                // ELSE: coverage between [1;255]:

                if (srcIn == null) {
                    // short-cut ?
//                    if (((extraAlpha << 16) | (am << 8) | (srcPixel[3])) == 0x7FFFFF) {
                    if ((am == NORM_BYTE) && (csa == NORM_BYTE) && (extraAlpha == NORM_BYTE7)) {
                        // mask with full opacity
                        // output = source OVER (totally)
                        // Source pixel Linear RGBA:
                        dstPixels[i] = srcRGBA;
                        continue;
                    }
                    // Copy prepared source pixel:
                    sr = csr;
                    sg = csg;
                    sb = csb;
                    sa = csa;
                } else {
                    // Source pixel Linear RGBA:
                    pixel = srcPixels[i];

                    // Linear RGBA components are not pre-multiplied by alpha:
                    // NOP
                    // Gamma-correction on Linear RGBA: 
                    // color components in range [0; 32385]
                    sr = gamma_dir[(pixel >> 16) & NORM_BYTE];
                    sg = gamma_dir[(pixel >> 8) & NORM_BYTE];
                    sb = gamma_dir[(pixel) & NORM_BYTE];
                    sa = (pixel >> 24) & NORM_BYTE;
                }
                // srcPixel is Gamma-corrected Linear RGBA.

                if (sa == 0) {
                    // Invisible Source
                    // result = destination (already the case) 
                    continue;
                }

                // fade operator:
                // Rs = As x Coverage
                // alpha in range [0; 32385] (15bits)
                // Apply extra alpha:
                sa = (sa * am * extraAlpha) / NORM_BYTE;

                // Ensure src not opaque to be properly blended below:
                if (sa == NORM_ALPHA) {
                    // mask with full opacity
                    // output = source OVER (totally)
                    // Source pixel Linear RGBA:
                    dstPixels[i] = (srcIn != null) ? srcPixels[i] : srcRGBA;
                    continue;
                }

                // Destination pixel:
                {
                    // Dest pixel Linear RGBA:
                    pixel = dstPixels[i];

                    // Linear RGBA components are not pre-multiplied by alpha:
                    // NOP
                    // Gamma-correction on Linear RGBA: 
                    // color components in range [0; 32385]
                    dr = gamma_dir[(pixel >> 16) & NORM_BYTE];
                    dg = gamma_dir[(pixel >> 8) & NORM_BYTE];
                    db = gamma_dir[(pixel) & NORM_BYTE];
                    da = (pixel >> 24) & NORM_BYTE;

                    // alpha in range [0; 32385] (15bits)
                    da *= NORM_BYTE7;
                }
                // dstPixel is Gamma-corrected Linear RGBA.

                if (FIX_CONTRAST) {
                    // in range [0; 32385] (15bits):
                    if (srcIn != null) {
                        ls = luminance(sr, sg, sb); // Y
                        lls = y2l_dir[ls]; // linear
                    }
                    ld = luminance(dr, dg, db); // Y
                    lld = y2l_dir[ld]; // linear

                    if (lls != lld) {
                        // [0; 255]
                        // (alpha * ls + (1-alpha) * ld) for alpha = 0.5 (midtone)
                        // sRGB classical: 0.5cov => half (use gamma_dirL to have linear comparisons (see eq 2)
//                        ll_old = y2l_dir[(128 * ls + 128 * ld) >> 8];
//                        lr = (128 * lls + 128 * lld) >> 8; // eq (1) linear RGB

                        // shifts faster (but better precision) :
                        ll_old = y2l_dir[(ls + ld + 1) >> 1];
                        lr = (lls + lld + 1) >> 1; // eq (1) linear RGB

                        // [0; 32385] / [0; 32385] 
                        // compare linear luminance:
                        contrast = (contrast_scale * (lr - ll_old)) / (lld - lls); // [0; 255] x 4 (eq 2)
//                            System.out.println("contrast: " + contrast);

                        // ALPHA in range [0; 32385] (15bits):
                        sa += (((sa * (NORM_ALPHA - sa)) / NORM_ALPHA) * contrast) / NORM_BYTE;
//                            System.out.println("Sa (fix): " + alpha);

                        // clamp alpha:
                        if (sa > NORM_ALPHA) {
                            // System.out.println("overflow : "+alpha);
                            sa = NORM_ALPHA;
                        } else if (sa < 0) {
                            // System.out.println("undeflow");
                            sa = 0;
                        }
                    }
                }

                // Ported-Duff rules in action:
                // R = S x fs + D x fd
                // Src Over Dst rule:
                // fs = Sa
                // fd = Da x (1 - Sa)
                // Factors in range [0; 32385] (15bits)
                fs = (sa); // alpha
                fd = (da * (NORM_ALPHA - sa)) / NORM_ALPHA;
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
                    dstPixels[i] = 0;
                    continue;
                }

                // color components in range [0; 32385]
                // no overflow: 15b + 15b < 31b
                rr = (sr * fs + dr * fd) / alpha;
                rg = (sg * fs + dg * fd) / alpha;
                rb = (sb * fs + db * fd) / alpha;
                // alpha in range [0; 255]
                ra = alpha / NORM_BYTE7;

                // From https://stackoverflow.com/questions/22607043/color-gradient-algorithm
                // Brightness or Relative Luminance correction ?
                if (FIX_LUM) {
                    lro = luminance(rr, rg, rb);

                    if (lro != 0) {
                        /*
                        gamma ← 0.43
                        brightness1 ← Pow(r1+g1+b1, gamma)
                        brightness2 ← Pow(r2+g2+b2, gamma)
                         */
                        ls = bright_dir[luminance(sr, sg, sb)];
                        ld = bright_dir[luminance(dr, dg, db)];

                        if (ls != ld) {
                            // Interpolate a new brightness value, and convert back to linear light
                            // brightness ← LinearInterpolation(brightness1, brightness2, mix)
                            lr = (ls * fs + ld * fd) / alpha;

                            // intensity ← Pow(brightness, 1 / gamma)
                            lr = bright_inv[lr];

                            if (lr != lro) {
                                // Apply adjustment factor to each rgb value based
                                /*
                                    if ((r+g+b) != 0) then
                                       factor ← (intensity / (r+g+b))
                                       r ← r * factor
                                       g ← g * factor
                                       b ← b * factor
                                    end if
                                 */
                                rr = (rr * lr) / lro;
                                rg = (rg * lr) / lro;
                                rb = (rb * lr) / lro;

                                // Check overflow:
                                if (rr > NORM_ALPHA) {
                                    rr = NORM_ALPHA;
                                }
                                if (rg > NORM_ALPHA) {
                                    rg = NORM_ALPHA;
                                }
                                if (rb > NORM_ALPHA) {
                                    rb = NORM_ALPHA;
                                }
                            }
                        }
                    }
                }

                // System.out.println("result: " + Arrays.toString(result));
                if (false) {
                    // Faster with explicit bound checks !
                    if (rr > NORM_ALPHA || rg > NORM_ALPHA || rb > NORM_ALPHA
                            || ra > NORM_BYTE) {
                        System.out.println("overflow");
                        rr = NORM_ALPHA;
                        rg = NORM_ALPHA;
                        rb = NORM_ALPHA;
                        ra = NORM_BYTE;
                    }
                    if (rr < 0 || rg < 0 || rb < 0 || ra < 0) {
                        System.out.println("underflow");
                        rr = 0;
                        rg = 0;
                        rb = 0;
                        ra = 0;
                    }
                }

                // result is Gamma-corrected Linear RGBA.
                // Inverse Gamma-correction on Linear RGBA: 
                // color components in range [0; 32385]
                pixel = (ra << 24)
                        | (gamma_inv[rr & MASK_ALPHA]) << 16
                        | (gamma_inv[rg & MASK_ALPHA]) << 8
                        | (gamma_inv[rb & MASK_ALPHA]);

                // Linear RGBA components are not pre-multiplied by alpha:
                // NOP
                dstPixels[i] = pixel;
            } // for
            dstOut.setDataElements(0, j, w, 1, dstPixels);
        }
    }
}
