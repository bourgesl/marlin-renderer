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
import static org.marlin.pipe.BlendComposite.LUM_MAX;
import static org.marlin.pipe.BlendComposite.MASK_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.TILE_WIDTH;
import static org.marlin.pipe.BlendComposite.USE_Y_TO_L;
import static org.marlin.pipe.BlendComposite.Y2L_LUT;
import static org.marlin.pipe.BlendComposite.brightness_LUT;
import static org.marlin.pipe.BlendComposite.luminance;
import static org.marlin.pipe.BlendComposite.luminance4b;

final class BlendingContextIntARGB extends BlendComposite.BlendingContext {

    private final static boolean USE_ALL_ALPHA = true;

    protected final static boolean DEBUG_ALPHA_LUT = false && USE_ALL_ALPHA;

    private final static AlphaLUT ALPHA_LUT = new AlphaLUT();

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

        final int[] bright_dir = (FIX_LUM) ? brightness_LUT.dir : null;
        final int[] bright_inv = (FIX_LUM) ? brightness_LUT.inv : null;

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
                        ls = luminance4b(sr, sg, sb); // Y (4bits)
                        src_alpha_tables = alpha_tables[ls & 0x0F];
                    }
                    // TODO: try caching last luminance computation ?
                    ld = luminance4b(dr, dg, db); // Y (4bits)

                    // ALPHA in range [0; 32385] (15bits):
                    sa = src_alpha_tables[ld & 0x0F][(sa >> 7) & NORM_BYTE];
//                    sa = src_alpha_tables[ld & 0x0F][((sa + 63) / NORM_BYTE7) & NORM_BYTE];
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

    final static class AlphaLUT {

        // TODO: use Unsafe (off-heap table)
        final int[][][] alphaTables;

        AlphaLUT() {
            alphaTables = new int[LUM_MAX + 1][LUM_MAX + 1][NORM_BYTE + 1];

            final int[] y2l_dir = (USE_Y_TO_L) ? Y2L_LUT.dirL : GAMMA_LUT.dirL;
            final int[] y2l_inv = (USE_Y_TO_L) ? Y2L_LUT.inv : GAMMA_LUT.inv;

            System.out.println("USE_Y_TO_L: " + USE_Y_TO_L);
            System.out.println("USE_ALL_ALPHA: " + USE_ALL_ALPHA);

            if (USE_ALL_ALPHA) {
                /*
                sRGB:
                    off=0: total rms (N=144) = 328
                    off=1: total rms (N=124) = 272
                    off=2: total rms (N=110) = 242
                    off=3: total rms (N=102) = 220
                    off=4: total rms (N=92) = 196
                    off=5: total rms (N=66) = 144
                    off=6: total rms (N=62) = 130
                    off=7: total rms (N=60) = 124
                Y_TO_L:
                    off=0: total rms (N=858) = 3542
                    off=1: total rms (N=68) = 206
                    off=2: total rms (N=430) = 978
                    off=3: total rms (N=232) = 494
                    off=4: total rms (N=116) = 244
                    off=5: total rms (N=106) = 238
                    off=6: total rms (N=90) = 192
                    off=7: total rms (N=94) = 204
                 */
                final int off = 4; // best compromise on rms error at the middle of the interval
//                for (int off = 0; off <= 7; off++)
                {
                    final long start = System.nanoTime();
                    int nd = 0;
                    long rms = 0l;

                    // Precompute alpha correction table:
                    // indexed by [ls | ld] => contrast factor
                    // ls / ld are Y (luminance) encoded on 7bits (enough ?)
                    for (int ls = 0; ls <= LUM_MAX; ls++) {
                        // [0; 127]:
                        final int sls = (ls * 8 + off); // +4 to be at the middle of the interval
                        //System.out.println("ls: " + ls + " => " + sls);

                        // [0; 32385]:
                        final int lls = y2l_dir[sls * NORM_BYTE]; // linear

                        for (int ld = 0; ld <= LUM_MAX; ld++) {
                            // [0; 127]:
                            final int sld = (ld * 8 + off); // +4 to be at the middle of the interval
                            // [0; 32385]:
                            final int lld = y2l_dir[sld * NORM_BYTE]; // linear

                            if (lls == lld) {
                                // no correction:
                                for (int a = 0; a <= NORM_BYTE; a++) {
                                    // [0; 32385]
                                    alphaTables[ls][ld][a] = a * NORM_BYTE7;
                                }
                            } else {
                                // [0; 255]
                                // (alpha * ls + (1-alpha) * ld) for alpha = 0.5 (midtone)
                                // sRGB classical: 0.5cov => half (use gamma_dirL to have linear comparisons (see eq 2)

                                for (int a = 0; a <= NORM_BYTE; a++) {
                                    // Precompute adjusted alpha:
                                    int alpha = a;

                                    if ((alpha != 0) && (alpha != NORM_BYTE)) {

                                        // shifts faster (but better precision) :
                                        // [0; 32385]: alpha in [0; 255] x [0; 7] * 16
                                        final int ll_old = y2l_dir[sls * alpha + sld * (NORM_BYTE - alpha)]; // round-off
                                        final int lr = (lls * alpha + lld * (NORM_BYTE - alpha) + NORM_BYTE7) / NORM_BYTE; // eq (1) linear RGB

                                        if (ll_old != lr) {
                                            // [0; 32385] / [0; 32385] 
                                            // compare linear luminance:
                                            final int delta = (NORM_BYTE7 * CONTRAST * (lr - ll_old)) / (lld - lls); // 64x 255 range

                                            if (delta != 0) {
                                                final int old_alpha = alpha;
//                                            System.out.println("delta: " + delta);
                                                alpha = (alpha * NORM_BYTE7 + delta + 63) / NORM_BYTE7;

                                                // clamp alpha:
                                                if (alpha > NORM_BYTE) {
                                                    alpha = NORM_BYTE;
                                                }
                                                if (alpha < 0) {
                                                    alpha = 0;
                                                }
                                                if (DEBUG_ALPHA_LUT) {
                                                    // test again equations:
                                                    // [0; 32385]:
                                                    final int l_old_test = sls * old_alpha + sld * (NORM_BYTE - old_alpha);
                                                    // [0; 32385]:
                                                    final int lr_test = y2l_inv[(lls * alpha + lld * (NORM_BYTE - alpha) + NORM_BYTE7) / NORM_BYTE]; // eq (1) linear RGB
                                                    // [0; 255]
                                                    final int diff = (USE_Y_TO_L) ? (l_old_test - lr_test + 63) / NORM_BYTE7
                                                            : (l_old_test - lr_test * NORM_BYTE7 + 63) / NORM_BYTE7;

                                                    if ((alpha != 0) && (alpha != NORM_BYTE)) {
                                                        if (Math.abs(diff) > 1) {
                                                            System.out.println("diff correction (old_alpha = " + old_alpha + " alpha = " + alpha + "): " + diff);
                                                            nd++;
                                                            rms += Math.abs(diff);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // [0; 32385]
                                    alphaTables[ls][ld][a] = alpha * NORM_BYTE7;
                                }
                            }
                        }
                    }

                    if (DEBUG_ALPHA_LUT) {
                        System.out.println("off=" + off + ": total rms (N=" + nd + ") = " + rms);
                        final long time = System.nanoTime() - start;
                        System.out.println("duration= " + (1e-6 * time) + " ms.");
                    }
                }
            } else {
                // old contrast approach: only fix alpha at 0.5

                // Precompute alpha correction table:
                // indexed by [ls | ld] => contrast factor
                // ls / ld are Y (luminance) encoded on 7bits (enough ?)
                for (int ls = 0; ls <= LUM_MAX; ls++) {
                    int lls = y2l_dir[ls * 8 * NORM_BYTE]; // linear

                    for (int ld = 0; ld <= LUM_MAX; ld++) {
                        int lld = y2l_dir[ld * 8 * NORM_BYTE]; // linear

                        int c = 0;

                        if (lls != lld) {
                            // [0; 255]
                            // (alpha * ls + (1-alpha) * ld) for alpha = 0.5 (midtone)
                            // sRGB classical: 0.5cov => half (use gamma_dirL to have linear comparisons (see eq 2)

                            // shifts faster (but better precision) :
                            int ll_old = y2l_dir[(ls + ld) * 8 * NORM_BYTE7]; // = 255 / 2
                            int lr = (lls + lld) >> 1; // eq (1) linear RGB

                            if (ll_old != lr) {
                                // [0; 32385] / [0; 32385] 
                                // compare linear luminance:
                                c = ((4 * CONTRAST) * (lr - ll_old)) / (lld - lls); // [0; 255] x 4 (eq 2)
                            }
                        }

                        for (int a = 0; a <= NORM_BYTE; a++) {
                            // Precompute adjusted alpha:
                            int alpha = a;

                            if (c != 0) {
                                alpha += ((alpha * (NORM_BYTE - alpha)) * c) / (NORM_BYTE * NORM_BYTE);

                                // clamp alpha:
                                if (alpha > NORM_BYTE) {
                                    alpha = NORM_BYTE;
                                }
                                if (alpha < 0) {
                                    alpha = 0;
                                }
                            }
                            // [0; 32385]
                            alphaTables[ls][ld][a] = alpha * NORM_BYTE7;
                        }
                    }
                }
            }
            // System.out.println("contrast: "+ Arrays.toString(contrast));
        }
    }
}
