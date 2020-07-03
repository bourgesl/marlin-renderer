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

import static org.marlin.pipe.BlendComposite.CONTRAST;
import static org.marlin.pipe.BlendComposite.LUM_MAX;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.LUMA_LUT;
import static org.marlin.pipe.MarlinCompositor.USE_CONTRAST_L;
import sun.misc.Unsafe;

final class AlphaLUT {

    protected final static boolean USE_BYTE = MarlinCompositor.BLEND_SPEED;

    protected final static boolean DEBUG_ALPHA_LUT = false;

    final static int LEN_ALPHA = NORM_BYTE + 1; // 1 << 8
    final static int LEN_LD = (LUM_MAX + 1) * LEN_ALPHA; // 1 << 8 or 10 (256 x 4)
    final static int LEN_LS = (LUM_MAX + 1) * LEN_LD; // 1 << 12 or 14 (16 x 256 x 4)

    final static AlphaLUT ALPHA_LUT = new AlphaLUT();

    // TODO: use Unsafe (off-heap table)
    final int[][][] alphaTables;

    final OffHeapArray LUT_UNSAFE;

    private AlphaLUT() {
        LUT_UNSAFE = new OffHeapArray(this, LEN_LS << 2); // ints (16 x 16 x 256)

        final Unsafe _unsafe = OffHeapArray.UNSAFE;
        final long at_addr = LUT_UNSAFE.start;        
        
        alphaTables = new int[LUM_MAX + 1][LUM_MAX + 1][LEN_ALPHA];

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
            final double dls = (USE_CONTRAST_L) ? (sls * NORM_BYTE) : LUMA_LUT.dir(sls * NORM_BYTE); // linear Y

            final long at_addr_ls = at_addr + (ls << 14L);

            for (int ld = 0; ld <= LUM_MAX; ld++) {
                final long at_addr_ld = at_addr_ls + (ld << 10L);

                if (ls == ld) {
                    // no correction:
                    for (int a = 0; a <= NORM_BYTE; a++) {
                        // [0; 32385]
                        alphaTables[ls][ld][a] = (USE_BYTE) ? a : (a * NORM_BYTE7);

                        _unsafe.putInt(at_addr_ld + (a << 2L), a);
                    }
                } else {
                    // [0; 127]:
                    final int sld = (ld * 8 + (ld >> 1)); // +4 to be at the middle of the interval
                    // [0; 32385]:
                    final double dld = (USE_CONTRAST_L) ? (sld * NORM_BYTE) : LUMA_LUT.dir(sld * NORM_BYTE); // linear Y

                    // [0; 255]
                    // (alpha * ls + (1-alpha) * ld) for alpha = 0.5 (midtone)
                    // sRGB classical: 0.5cov => half (use gamma_dirL to have linear comparisons (see eq 2)
                    for (int a = 0; a <= NORM_BYTE; a++) {
                        // Precompute adjusted alpha:
                        int alpha = a * NORM_BYTE7;

                        if ((alpha != 0) && (alpha != NORM_ALPHA)) {
                            final int old_alpha = alpha;

                            final double dl_old;

                            // TODO: adapt flag LUMA_LUT vs L*
                            if (USE_CONTRAST_L) {
                                // Use L*(CIE) to interpolate among L*(Y) values:
                                // linear interpolation on L -> Y
                                // [0; 32385]:
                                dl_old = LUMA_LUT.dir(
                                        (LUMA_LUT.inv(dls) * old_alpha + LUMA_LUT.inv(dld) * (NORM_ALPHA - old_alpha)) / NORM_ALPHA
                                );
                            } else {
                                // Use LUMA_LUT(Linear RGB) to interpolate among Y values:
                                // linear interpolation on Y
                                // [0; 32385]:
                                dl_old = LUMA_LUT.dir(
                                        (sls * old_alpha + sld * (NORM_ALPHA - old_alpha) + 63) / NORM_BYTE7
                                );
                            }

                            if (dl_old != dld) {
                                // [0; 32385] / [0; 32385] 
                                alpha = (int) Math.round(((NORM_BYTE7 * CONTRAST) * (dl_old - dld)) / (dls - dld)); // 127 x 255 range

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

                                    /*
                                        TODO: fix debug code to support contrastL method:
                                     */
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
                        }
                        if (USE_BYTE) {
                            // [0; 255]
                            alpha = (alpha + 63) / NORM_BYTE7;
                            alphaTables[ls][ld][a] = alpha;

                            _unsafe.putInt(at_addr_ld + (a << 2L), alpha);
                        } else {
                            // [0; 32385]
                            alphaTables[ls][ld][a] = alpha;
                        }
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
