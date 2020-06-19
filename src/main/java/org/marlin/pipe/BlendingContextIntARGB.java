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
import static org.marlin.pipe.BlendComposite.LUM_MAX;
import static org.marlin.pipe.BlendComposite.MASK_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.TILE_WIDTH;
import static org.marlin.pipe.BlendComposite.luminance;
import static org.marlin.pipe.BlendComposite.luminance4b;
import static org.marlin.pipe.BlendComposite.LUMA_LUT;
import static org.marlin.pipe.MarlinCompositor.FIX_CONTRAST;
import static org.marlin.pipe.MarlinCompositor.FIX_LUM;
import static org.marlin.pipe.BlendComposite.L_TO_Y_LUT;

final class BlendingContextIntARGB extends BlendComposite.BlendingContext {

    protected final static boolean DEBUG_ALPHA_LUT = false;

    private final static AlphaLUT ALPHA_LUT = new AlphaLUT();

    private final static boolean OPT_DIVIDE = false;

    // recycled arrays into context (shared):
    // horiz arrays:
    private int[] _srcPixels = new int[TILE_WIDTH];
    private int[] _dstPixels = new int[TILE_WIDTH];

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
                 final byte[] mask, final int maskoff, final int maskscan) {

        /*
             System.out.println("srcIn = " + srcIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int[] gamma_dir = GAMMA_LUT.dir;
        final int[] gamma_inv = GAMMA_LUT.inv;

        final int[] luma_dir = LUMA_LUT.dir;
        final int[] luma_inv = LUMA_LUT.inv;

        final int[][][] alpha_tables = ALPHA_LUT.alphaTables;
        int[][] src_alpha_tables = null;

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

        int ls = 0;

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
                ls = luminance4b(csr, csg, csb); // Y (4bits)
                src_alpha_tables = alpha_tables[ls & 0x0F];
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

        int ld, lro, lr;

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
                sa = (sa * am * extraAlpha) / NORM_BYTE; // TODO: avoid divide and use [0; 255]

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
                        ls = luminance4b(sr, sg, sb); // Y (4bits)
                        src_alpha_tables = alpha_tables[ls & 0x0F];
                    }
                    // TODO: try caching last luminance computation ?
                    ld = luminance4b(dr, dg, db); // Y (4bits)

                    // ALPHA in range [0; 32385] (15bits):
                    if (OPT_DIVIDE) {
                        sa = src_alpha_tables[ld & 0x0F][(((sa + 65) * 129) >> 14) & NORM_BYTE]; // NO DIVIDE
                    } else {
                        sa = src_alpha_tables[ld & 0x0F][((sa + 63) / NORM_BYTE7) & NORM_BYTE]; // DIVIDE
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
                if (OPT_DIVIDE) {
                    ra = (((alpha + 65) * 129) >> 14); // NO DIVIDE
                } else {
                    ra = alpha / NORM_BYTE7; // DIVIDE
                }

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

    final static class AlphaLUT {

        // TODO: use Unsafe (off-heap table)
        final int[][][] alphaTables;

        AlphaLUT() {
            alphaTables = new int[LUM_MAX + 1][LUM_MAX + 1][NORM_BYTE + 1];

            final long start = System.nanoTime();
            int nd = 0;
            long rms = 0l;

            // Precompute alpha correction table:
            // indexed by [ls | ld] => contrast factor
            // ls / ld are Y (luminance) encoded on 7bits (enough ?)
            for (int ls = 0; ls <= LUM_MAX; ls++) {
                // [0; 127]:
                final int sls = (ls * 8 + (ls >> 1)); // +4 to be at the middle of 16 intervals in [0; 127]
                //System.out.println("ls: " + ls + " => " + sls);

                // [0; 32385]:
                final double dls = LUMA_LUT.dir(sls * NORM_BYTE); // linear Y -> L

                for (int ld = 0; ld <= LUM_MAX; ld++) {
                    if (ls == ld) {
                        // no correction:
                        for (int a = 0; a <= NORM_BYTE; a++) {
                            // [0; 32385]
                            alphaTables[ls][ld][a] = a * NORM_BYTE7;
                        }
                    } else {
                        // [0; 127]:
                        final int sld = (ld * 8 + (ld >> 1)); // +4 to be at the middle of the interval
                        // [0; 32385]:
                        final double dld = LUMA_LUT.dir(sld * NORM_BYTE); // linear Y -> L

                        // [0; 255]
                        // (alpha * ls + (1-alpha) * ld) for alpha = 0.5 (midtone)
                        // sRGB classical: 0.5cov => half (use gamma_dirL to have linear comparisons (see eq 2)
                        for (int a = 0; a <= NORM_BYTE; a++) {
                            // Precompute adjusted alpha:
                            int alpha = a * NORM_BYTE7;

                            if ((alpha != 0) && (alpha != NORM_ALPHA)) {
                                final int old_alpha = alpha;

                                // TODO: adapt flag LUMA_LUT vs L*
                                if (true) {
                                    // Use L*(CIE) to interpolate among L*(Y) values:
                                    final double lls = L_TO_Y_LUT.inv(sls * NORM_BYTE);
                                    final double lld = L_TO_Y_LUT.inv(sld * NORM_BYTE);

                                    final double dl_old = L_TO_Y_LUT.dir((lls * old_alpha + lld * (NORM_ALPHA - old_alpha)) / NORM_ALPHA); // linear Y -> L

                                    alpha = (int) Math.round(((NORM_BYTE7 * CONTRAST) * (dl_old - sld * NORM_BYTE)) / ((sls - sld) * NORM_BYTE));
                                } else {
                                    // Use LUMA_LUT(Linear RGB) to interpolate among Y values:

                                    // shifts faster (but better precision) :
                                    // [0; 32385]: alpha in [0; 255] x [0; 7] * 16
                                    final double dl_old = LUMA_LUT.dir((sls * old_alpha + sld * (NORM_ALPHA - old_alpha) + 63) / NORM_BYTE7); // linear Y -> L

                                    if (dl_old != dld) {
                                        // [0; 32385] / [0; 32385] 
                                        // compare linear luminance:
                                        alpha = (int) Math.round(((NORM_BYTE7 * CONTRAST) * (dl_old - dld)) / (dls - dld)); // 127 x 255 range
                                        // System.out.println("alpha (" + old_alpha + ") : " + alpha);
                                    }
                                }

                                // clamp alpha:
                                if (alpha > NORM_ALPHA) {
                                    alpha = NORM_ALPHA;
                                }
                                if (alpha < 0) {
                                    alpha = 0;
                                }
                                if (DEBUG_ALPHA_LUT) {
                                    if (alpha != old_alpha) {
                                        System.out.println("alpha: " + alpha + " old_alpha: " + old_alpha);
                                    }
                                    // test again equations:
                                    // [0; 32385]:
                                    final int l_old_test = (sls * old_alpha + sld * (NORM_ALPHA - old_alpha) + 63) / NORM_BYTE7;
                                    // [0; 32385]:
                                    final int lr_test = (int) Math.round(LUMA_LUT.inv((dls * alpha + dld * (NORM_ALPHA - alpha)) / NORM_ALPHA)); // eq (1) linear RGB
                                    // [0; 255]
                                    final int diff = (l_old_test - lr_test + 63) / NORM_BYTE7;

                                    System.out.println("diff l_old_test: " + (l_old_test - lr_test));

                                    if ((alpha != 0) && (alpha != NORM_ALPHA)) {
                                        if (Math.abs(diff) >= 1) {
                                            System.out.println("diff correction (old_alpha = " + old_alpha + " alpha = " + alpha + "): " + diff);
                                            nd++;
                                            rms += Math.abs(diff);
                                        }
                                    }
                                }
                            }
                            // [0; 32385]
                            alphaTables[ls][ld][a] = alpha;
                        }
                    }
                }
            }

            if (DEBUG_ALPHA_LUT) {
                System.out.println("total rms (N=" + nd + ") = " + rms);
                final long time = System.nanoTime() - start;
                System.out.println("duration= " + (1e-6 * time) + " ms.");
            }

            // TODO: test all luminance values in [0; 127] x [0; 127]
        }
    }
}
