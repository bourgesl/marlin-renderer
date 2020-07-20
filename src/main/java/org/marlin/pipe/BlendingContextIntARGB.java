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
import static org.marlin.pipe.BlendComposite.luminance;
import static org.marlin.pipe.BlendComposite.luminance10b;

final class BlendingContextIntARGB extends BlendComposite.BlendingContext {

    private final static boolean DO_DIVIDE = false;

    // recycled arrays into context (shared):
    // horiz arrays:
    private int[] _srcPixels = new int[BlendComposite.TILE_WIDTH];
    private int[] _dstPixels = new int[BlendComposite.TILE_WIDTH];

    BlendingContextIntARGB() {
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
                 final byte[] mask, final int maskOff, final int maskScan) {

        /*
             System.out.println("srcIn = " + srcIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int extraAlpha = this._extraAlpha; // 7 bits
        final boolean doEA = (extraAlpha != BlendComposite.NORM_BYTE7);

        final int[] gamma_dir = BlendComposite.GAMMA_LUT.dir;
        final int[] gamma_inv = BlendComposite.GAMMA_LUT.inv;

        final int[] luma_dir = BlendComposite.LUMA_LUT.dir;
        final int[] luma_inv = BlendComposite.LUMA_LUT.inv;

        final int MASK_ALPHA = BlendComposite.MASK_ALPHA;
        final int NORM_ALPHA = BlendComposite.NORM_ALPHA;
        final int NORM_BYTE = BlendComposite.NORM_BYTE;
        final int NORM_BYTE7 = BlendComposite.NORM_BYTE7;

        final int[] luma_table = AlphaLUT.ALPHA_LUT.lumaTable;
        final int[][][] alpha_tables = AlphaLUT.ALPHA_LUT.alphaTables;
        int[][] src_alpha_tables = null;

//            final BlendComposite.Blender blender = _blender;
        // use shared arrays:
        final int[] srcPixels = (srcIn != null) ? getSrcPixels(w) : null;
        final int[] dstPixels = getDstPixels(w);

        int pixel;
        int csr, csg, csb, csa;
        int sr, sg, sb, sa;
        int dr, dg, db, da;
        int rr, rg, rb, ra;

        int ls = 0;

        // Prepare source pixel if constant in tile:
        if (srcIn == null) {
            // Source pixel Linear RGBA:
            pixel = srcRGBA;

            // Linear RGBA components are not pre-multiplied by alpha:
            // NOP
            // Gamma-correction on Linear RGBA: 
            // color components in range [0; 32385]
            csa = (pixel >> 24) & NORM_BYTE;

            if (csa == 0) {
                // Invisible Source
                // result = destination (already the case)
                return;
            }

            csr = gamma_dir[(pixel >> 16) & NORM_BYTE];
            csg = gamma_dir[(pixel >> 8) & NORM_BYTE];
            csb = gamma_dir[(pixel) & NORM_BYTE];

            // c_srcPixel is Gamma-corrected Linear RGBA.
            if (MarlinCompositor.FIX_CONTRAST) {
                ls = luminance10b(csr, csg, csb); // Y (10bits)
                src_alpha_tables = alpha_tables[luma_table[ls]];
            }
            if (MarlinCompositor.FIX_LUM) {
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

        int ld, lro, lr;

        for (int j = 0; j < h; j++) {
            // TODO: use directly DataBufferInt (offsets + stride)
            if (srcIn != null) {
                srcIn.getDataElements(0, j, w, 1, srcPixels);
            }
            dstOut.getDataElements(0, j, w, 1, dstPixels);

            offTile = j * maskScan + maskOff;

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
                    sa = (pixel >> 24) & NORM_BYTE;

                    if (sa == 0) {
                        // Invisible Source
                        // result = destination (already the case) 
                        continue;
                    }

                    if (doEA) {
                        if (DO_DIVIDE) {
                            sa = (sa * (extraAlpha << 1)) / NORM_BYTE;
                        } else {
                            sa *= (extraAlpha << 1);
                            sa = (sa + ((sa + 257) >> 8)) >> 8; // div 255
                        }
                    }
                    sr = gamma_dir[(pixel >> 16) & NORM_BYTE];
                    sg = gamma_dir[(pixel >> 8) & NORM_BYTE];
                    sb = gamma_dir[(pixel) & NORM_BYTE];
                }
                // srcPixel is Gamma-corrected Linear RGBA.

                // short-cut ?
                if ((am == NORM_BYTE) && (sa == NORM_BYTE)) {
                    // mask with full opacity
                    // output = source OVER (totally)
                    // Source pixel Linear RGBA:
                    dstPixels[i] = (srcIn != null) ? srcPixels[i] : srcRGBA;
                    continue;
                }

                // fade operator:
                // Rs = As x Coverage
                // alpha in range [0; 32385] (15bits)
                // Apply extra alpha:
                sa = (sa >> 1) * am;

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

                if (MarlinCompositor.FIX_CONTRAST) {
                    // in range [0; 32385] (15bits):
                    if (srcIn != null) {
                        ls = luminance10b(csr, csg, csb); // Y (10bits)
                        src_alpha_tables = alpha_tables[luma_table[ls]];
                    }
                    ld = luminance10b(dr, dg, db); // Y (10bits)

                    // ALPHA in range [0; 32385] (15bits):
                    if (DO_DIVIDE) {
                        sa = src_alpha_tables[luma_table[ld]][(((sa + 65) * 129) >> 14) & NORM_BYTE]; // NO DIVIDE
                    } else {
                        sa = src_alpha_tables[luma_table[ld]][((sa + 63) / NORM_BYTE7) & NORM_BYTE]; // DIVIDE
                    }
                }

                // Ported-Duff rules in action:
                // R = S x fs + D x fd
                // Src Over Dst rule:
                // fs = Sa
                // fd = Da x (1 - Sa)
                // Factors in range [0; 32385] (15bits)
                fs = (sa); // alpha
                fd = (da * (NORM_ALPHA - sa)) / NORM_ALPHA; // TODO: avoid divide and use [0; 255]

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
                if (DO_DIVIDE) {
                    ra = (((alpha + 65) * 129) >> 14); // NO DIVIDE
                } else {
                    ra = alpha / NORM_BYTE7; // DIVIDE
                }

                // Brightness or Relative Luminance correction :
                if (MarlinCompositor.FIX_LUM) {
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
