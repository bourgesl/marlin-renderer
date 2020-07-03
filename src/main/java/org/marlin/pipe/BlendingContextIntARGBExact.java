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
import static org.marlin.pipe.BlendComposite.GAMMA_LUT;
import static org.marlin.pipe.BlendComposite.MASK_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.TILE_WIDTH;
import static org.marlin.pipe.BlendComposite.luminance;
import static org.marlin.pipe.BlendComposite.LUMA_LUT;
import static org.marlin.pipe.MarlinCompositor.FIX_CONTRAST;
import static org.marlin.pipe.MarlinCompositor.FIX_LUM;

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
        final int[] gamma_inv = GAMMA_LUT.inv;

        final int[] luma_dir = LUMA_LUT.dir;
        final int[] luma_inv = LUMA_LUT.inv;

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
        double dls = 0.0;

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
                lls = luma_dir[ls]; // linear
                dls = LUMA_LUT.dir(ls);

                // System.out.println("sb: " + csb + " ls: " + ls + " lls: " + lls + " dls: " + dls);
            }
            if (FIX_LUM) {
                ls = luma_inv[luminance(csr, csg, csb)];
            }
        } else {
            csr = 0;
            csg = 0;
            csb = 0;
            csa = 0;
        }

        int am, alpha, fs, fd;
        int offTile;

        int ld, lld, ll_old, lro, lr;
        double dld, dl_old, dlro, dlr;

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
                    if ((am == NORM_BYTE) && (csa == NORM_BYTE)) {
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
                    
                    if (extraAlpha != NORM_BYTE7) {
                         sa = (sa * extraAlpha + 63) / NORM_BYTE7;
                    }
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
                sa = (sa >> 1) * am;

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
                        lls = luma_dir[ls]; // linear
                        dls = LUMA_LUT.dir(ls);
                    }
                    ld = luminance(dr, dg, db); // Y

                    lld = luma_dir[ld]; // linear
                    dld = LUMA_LUT.dir(ld);

                    // System.out.println("db: " + db + " ld: " + ld + " lld: " + lld + " dld: " + dld + " sa: " + sa);

                    if (dls != dld) {
                        // Precompute adjusted alpha:
                        alpha = sa;

                        // shifts faster (but better precision) :
                        // [0; 32385]: alpha in [0; 255] x [0; 7] * 16
                        dl_old = LUMA_LUT.dir((ls * alpha + ld * (NORM_ALPHA - alpha)) / NORM_ALPHA); // round-off
                        dlr = (dls * alpha + dld * (NORM_ALPHA - alpha)) / NORM_ALPHA; // eq (1) linear RGB

                        // System.out.println("dl_old: " + dl_old + " dlr: " + dlr);

                        if (dl_old != dlr) {
                            // [0; 32385] / [0; 32385] 
                            // compare linear luminance:
                            final int delta = (int)Math.round(((NORM_BYTE7 * CONTRAST) * (dlr - dl_old)) / (dld - dls)); // 127 x 255 range

                            // System.out.println("alpha: " + alpha + " delta: " + delta);

                            if (delta != 0) {
                                alpha += delta;

                                // clamp alpha:
                                if (alpha > NORM_ALPHA) {
                                    alpha = NORM_ALPHA;
                                }
                                if (alpha < 0) {
                                    alpha = 0;
                                }
                                // Fix ALPHA in range [0; 32385] (15bits):
                                sa = alpha;
                            }
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

                // Brightness or Relative Luminance correction :
                if (FIX_LUM) {
                    // Idea from https://stackoverflow.com/questions/22607043/color-gradient-algorithm

                    // linear RGB luminance Result :
                    lro = luminance(rr, rg, rb);

                    if (lro != 0) {
                        // perceptual luminance (linear RGB) :
                        // in range [0; 32385] (15bits):
                        if (srcIn != null) {
                            ls = luma_inv[luminance(sr, sg, sb)];
                        }
                        ld = luma_inv[luminance(dr, dg, db)];

                        if (ls != ld) {
                            // linear interpolation of perceptual luminance
                            lr = (ls * fs + ld * fd) / alpha;

                            // Fixed linear RGB luminance Result
                            lr = luma_dir[lr];

                            if (lr != lro) {
                                // proportional correction of linear RGB luminance
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
