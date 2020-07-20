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

import java.util.Arrays;
import static org.marlin.pipe.BlendComposite.CONTRAST;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import static org.marlin.pipe.BlendComposite.LUMA_LUT;
import static org.marlin.pipe.MarlinCompositor.FIX_CONTRAST;
import static org.marlin.pipe.MarlinCompositor.IS_PERCEPTUAL;
import static org.marlin.pipe.MarlinCompositor.USE_CONTRAST_L;
import static org.marlin.pipe.MarlinCompositor.USE_OLD_BLENDER;
import sun.misc.Unsafe;

public final class AlphaLUT {

    protected final static boolean USE_BYTE = MarlinCompositor.BLEND_SPEED;

    public final static double L_THRESHOLD = 3.4 / 100; // upper limit set lower than 4% L* sampling error

    public final static int MAX_PREC = 1024; // Luma Y precision requirement
    public final static int MAX_LUMA = MAX_PREC - 1;

    private final static int MAX_TABLES = 32; // upper limit in memory usage (27 typical with 1024 precision for 4% error)

    protected final static boolean DEBUG_ALPHA_LUT = false;

    static {
        if (DEBUG_ALPHA_LUT && (!IS_PERCEPTUAL || (CONTRAST != NORM_BYTE))) {
            throw new IllegalStateException("AlphaLUT: set compositor.mode=perceptual and contrast=1.0 for DEBUG_ALPHA_LUT to have normal errors");
        }
    }

    private final static int LEN_ALPHA = NORM_BYTE + 1;

    // singleton:
    public final static AlphaLUT ALPHA_LUT = new AlphaLUT();

    public final int[] lumaTable;
    public final int[] lumaValues;

    public final int[][][] alphaTables; // may be null if unsafe tables are used.

    final OffHeapArray LUMA_TABLE_UNSAFE;

    final OffHeapArray ALPHA_INDEX_UNSAFE;
    final OffHeapArray ALPHA_TABLES_UNSAFE;

    private AlphaLUT() {
        // final long start = System.nanoTime();

        /* 
        L*(Y) needs Y precision up to 1.0/256 = 0,00390625 (to have L* = 3.5%) eye is limited to 4 or 5% !
        => 100 / 3.5% => 28 tables on each axis (L1, L2). Maybe store only delta (not alpha) to have symety (half tables) ?
        
        Notes:
        - always keep Y=0 and Y=1 exterma (very often)
        - keep only 1 identity table (ls = ld)
         */
        // Make sampling L*(Y) adaptative:
        int nLuma = 0;
        {
            int[] lumaArray = new int[MAX_TABLES];
            double[] lArray = new double[MAX_TABLES];

            double lastL = 0.0, prevL = lastL;

            // Find intervals in Luma(Y):
            // [0, 1023]
            for (int il = 0; il <= MAX_LUMA; il++) {
                final boolean isBorder = (il == 0) || (il == MAX_LUMA);
                // [0; 32385]:
                final double dl = ((double) (il * NORM_ALPHA) / MAX_LUMA); // linear Y

                // [0; 32385] to [0; 1]:
                final double l = LUMA_LUT.inv(dl) / NORM_ALPHA;

                if (((l - lastL) > L_THRESHOLD) || isBorder) {
                    final int real_il;
                    final double real_l;

                    if (!isBorder && (lastL != prevL)) {
                        real_il = (il - 1);
                        real_l = prevL;
                    } else {
                        real_il = il;
                        real_l = l;
                    }
                    lumaArray[nLuma] = real_il;
                    lArray[nLuma] = real_l;
                    nLuma++;

                    if (!isBorder && (nLuma == MAX_TABLES)) {
                        throw new IllegalStateException("AlphaLUT: WARNING: MAX_TABLES = " + MAX_TABLES + " is too small !");
                    }

                    final double y = Math.round(1000.0 * real_il / MAX_LUMA) / 1000.0;
                    final double diff = Math.round(1000.0 * 100.0 * (real_l - lastL)) / 1000.0;

                    if (DEBUG_ALPHA_LUT) {
                        System.out.println(
                                "il: " + real_il
                                + " Y: " + y
                                + " L*: " + (Math.round(1000.0 * real_l) / 1000.0)
                                + " diff: " + diff);
                    }

                    lastL = real_l;
                }
                prevL = l;
            }

            lumaValues = Arrays.copyOf(lumaArray, nLuma);
            lumaArray = null; // early gc

            if (DEBUG_ALPHA_LUT) {
                System.out.println("Luma samples: " + nLuma);
                System.out.println("Luma array  : " + Arrays.toString(lumaValues));
                System.out.println("L*   array  : " + Arrays.toString(lArray));
            }

            // Remap Luma(Y) on intervals ie make Luma indexing onto Y values.
            // Traverse intervals:
            lumaTable = new int[MAX_PREC];
            final int i0 = 0;
            int y0 = lumaValues[i0];
            double l0 = lArray[i0];

            lumaTable[0] = i0;

            for (int i = 1; i < nLuma; i++) {
                final int y1 = lumaValues[i];
                final double l1 = lArray[i];

                if (DEBUG_ALPHA_LUT && false) {
                    System.out.println("Y0: " + y0 + " L: " + l0 + " Y1: " + y1 + " L: " + l1);
                }

                // traverse l0 -> l1
                for (int j = y0; j < y1; j++) {
                    // [0; 32385]:
                    final double dl = ((double) (j * NORM_ALPHA) / MAX_LUMA); // linear Y

                    // [0; 32385] to [0; 1]:
                    final double l = LUMA_LUT.inv(dl) / NORM_ALPHA;

                    if (l - l0 < l1 - l) {
                        // closer to P0:
                        lumaTable[j] = i - 1;
                    } else {
                        // closer to P1:
                        lumaTable[j] = i;
                    }

                    if (DEBUG_ALPHA_LUT && false) {
                        System.out.println("J: " + j + " L : " + l + " ==> " + lumaTable[j]);
                    }
                }

                // add P1
                lumaTable[y1] = i;

                // go on
                y0 = y1;
                l0 = l1;
            }
            if (DEBUG_ALPHA_LUT) {
                System.out.println("Luma table  : " + Arrays.toString(lumaTable));
            }
            lArray = null; // early gc
        }

        if (!MarlinCompositor.BLEND_SPEED_COLOR && !USE_OLD_BLENDER && !MarlinCompositor.BLEND_QUALITY) {
            alphaTables = new int[nLuma][nLuma][];
        } else {
            alphaTables = null;
        }

        final Unsafe _unsafe = OffHeapArray.UNSAFE;

        // Copy LumtTable:
        LUMA_TABLE_UNSAFE = new OffHeapArray(this, lumaTable.length << 2L); // ints (1024 x 4 = 4K)
        final long lt_addr = LUMA_TABLE_UNSAFE.start;

        for (int i = 0; i < lumaTable.length; i++) {
            _unsafe.putInt(lt_addr + (i << 2L), lumaTable[i]);
        }

        ALPHA_INDEX_UNSAFE = new OffHeapArray(this, (MAX_TABLES * MAX_TABLES) << 2L); // ints (32 x 32 x 4 = 4K)
        final long ai_addr = ALPHA_INDEX_UNSAFE.start;

        // Precompute alpha correction table:
        // indexed by [ls | ld] => contrast factor
        // New alpha table prepared on 1024 precision (10bits):
        // il is Y (luminance) encoded on 10bits (enough, yes)
        // note: max 32 * 32 = 1024 tables (256 * 4 = 1ko) ie max 1Mb.
        int nTablesPrior = 0;
        if (FIX_CONTRAST) {
            nTablesPrior = (nLuma * (nLuma - 1));
            if (!IS_PERCEPTUAL) {
                nTablesPrior /= 2;
            }
        }
        nTablesPrior++;

        ALPHA_TABLES_UNSAFE = new OffHeapArray(this, (nTablesPrior * LEN_ALPHA) << 2); // ints (32 x 32 x 256) x 4
        final long at_addr = ALPHA_TABLES_UNSAFE.start;

        final long offset_identity = 0;
        long at_addr_a = at_addr + offset_identity;

        int nTables = 0;

        // Prepare identity table:
        final int[] alpha_identity = new int[LEN_ALPHA];
        // [0; 255]
        for (int a = 0; a <= NORM_BYTE; a++) {
            // Precompute adjusted alpha:
            int alpha = a * NORM_BYTE7;
            // anyway:
            if (USE_BYTE) {
                // [0; 255]
                alpha = (alpha + 63) / NORM_BYTE7;
                alpha_identity[a] = alpha;
                _unsafe.putInt(at_addr_a + (a << 2), alpha);
            } else {
                // [0; 32385]
                alpha_identity[a] = alpha;
            }
        }
        nTables++;

        // [0; nLuma]
        for (int ixls = 0; ixls < nLuma; ixls++) {
            // y value:
            final int ils = lumaValues[ixls];
            // [0; 32385]:
            final double dls = (USE_CONTRAST_L) ? ((ils * NORM_ALPHA) / MAX_LUMA) : LUMA_LUT.dir((ils * NORM_ALPHA) / MAX_LUMA); // linear Y

            // [0; 1023]
            for (int ixld = 0; ixld < nLuma; ixld++) {
                // y value:
                final int ild = lumaValues[ixld];
                // [0; 32385]:
                final double dld = (USE_CONTRAST_L) ? ((ild * NORM_ALPHA) / MAX_LUMA) : LUMA_LUT.dir((ild * NORM_ALPHA) / MAX_LUMA); // linear Y

                int[] alpha_table = null;
                final int offset_table;

                if ((ixls == ixld) || (!IS_PERCEPTUAL && (ixls > ixld)) || !(FIX_CONTRAST && (CONTRAST != 0))) {
                    // identity table
                    if (alphaTables != null) {
                        alpha_table = alpha_identity;
                    }
                    offset_table = 0;
                } else {
                    /*
                        nTables (max): 1024 (32*32)
                        nTables (needed): 496 (less than half)
                     */
                    if (alphaTables != null) {
                        alpha_table = new int[LEN_ALPHA];
                    }
                    offset_table = (nTables * LEN_ALPHA) << 2;
                    at_addr_a = at_addr + offset_table;
                    nTables++;

                    // [0; 255]
                    for (int a = 0; a <= NORM_BYTE; a++) {
                        // Precompute adjusted alpha:
                        int alpha = a * NORM_BYTE7;

                        if ((alpha != 0) && (alpha != NORM_ALPHA)) {
                            final int old_alpha = alpha;

                            final double dl_old;
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
                                        (((double) (ils * old_alpha + ild * (NORM_ALPHA - old_alpha))) / MAX_LUMA)
                                );
                            }

                            if (dl_old != dld) {
                                // [0; 32385] / [0; 32385] 
                                alpha = (int) Math.round((NORM_ALPHA * (dl_old - dld)) / (dls - dld)); // 127 x 255 range

                                if (alpha != old_alpha) {
                                    // Only add light, never remove:
                                    if (!IS_PERCEPTUAL && (alpha < old_alpha)) {
                                        alpha = old_alpha;
                                    } else if (CONTRAST != NORM_BYTE) {
                                        // adjust contrast:
                                        alpha = old_alpha + ((alpha - old_alpha) * CONTRAST + NORM_BYTE7) / NORM_BYTE;
                                    }
                                    // clamp alpha:
                                    if (alpha > NORM_ALPHA) {
                                        alpha = NORM_ALPHA;
                                    }
                                    if (alpha < 0) {
                                        alpha = 0;
                                    }
                                }
                            }
                        }
                        // anyway:
                        if (USE_BYTE) {
                            // [0; 255]
                            alpha = (alpha + 63) / NORM_BYTE7;
                            if (alpha_table != null) {
                                alpha_table[a] = alpha;
                            }
                            _unsafe.putInt(at_addr_a + (a << 2), alpha);
                        } else {
                            // [0; 32385]
                            if (alpha_table != null) {
                                alpha_table[a] = alpha;
                            }
                        }
                    }
                }
                if (alphaTables != null) {
                    alphaTables[ixls][ixld] = alpha_table;
                }
                // (ixls * 32 + ixld) * 4 <=> (ixls << 5 + ixld) << 2
                _unsafe.putInt(ai_addr + ((ixls << 7) + (ixld << 2)), offset_table);
            }
        }
        if (DEBUG_ALPHA_LUT) {
            System.out.println("nTables (created): " + nTables);
        }

        if (DEBUG_ALPHA_LUT) {
            int nd = 0;
            int max = 0;
            long rms = 0l;
            final int[][] rmsTable = new int[nLuma][nLuma];
            final int[][] maxTable = new int[nLuma][nLuma];

            // Test all luminance values ie [0.. 255] and compare with approximation (16 levels only):
            // TODO: test all luminance values in [0; 127] x [0; 127]
            // [0; 255]:
            for (int sls = 0; sls <= NORM_BYTE7; sls++) {
                // convert to new scale [0; 1023]:
                final int ixls = lumaTable[(sls * MAX_LUMA) / NORM_BYTE7];
                // [0; 32385]:
                final double dls = (USE_CONTRAST_L) ? (sls * NORM_BYTE) : LUMA_LUT.dir(sls * NORM_BYTE); // linear Y

                for (int sld = 0; sld <= NORM_BYTE7; sld++) {
                    // convert to new scale [0; 1023]:
                    final int ixld = lumaTable[(sld * MAX_LUMA) / NORM_BYTE7];
                    // [0; 32385]:
                    final double dld = (USE_CONTRAST_L) ? (sld * NORM_BYTE) : LUMA_LUT.dir(sld * NORM_BYTE); // linear Y

                    // [0; 255]
                    for (int a = 0; a <= NORM_BYTE; a++) {
                        final int approx_alpha
                                  = (alphaTables != null) ? alphaTables[ixls][ixld][a]
                                        : _unsafe.getInt(at_addr
                                                + _unsafe.getInt(ai_addr + ((ixls << 7) + (ixld << 2)))
                                                + (a << 2));
                        int alpha;

                        if (sls == sld) {
                            // no correction:
                            // [0; 32385]
                            alpha = (USE_BYTE) ? a : (a * NORM_BYTE7);
                        } else {
                            // Precompute adjusted alpha:
                            alpha = a * NORM_BYTE7;

                            if ((alpha != 0) && (alpha != NORM_ALPHA)) {
                                // No contrast here
                                final int old_alpha = alpha;

                                final double dl_old;
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
                                            ((double) (sls * old_alpha + sld * (NORM_ALPHA - old_alpha))) / NORM_BYTE7
                                    );
                                }

                                if (dl_old != dld) {
                                    // [0; 32385] / [0; 32385] 
                                    alpha = (int) Math.round((NORM_ALPHA * (dl_old - dld)) / (dls - dld)); // 127 x 255 range

                                    if (alpha != old_alpha) {
                                        // clamp alpha:
                                        if (alpha > NORM_ALPHA) {
                                            alpha = NORM_ALPHA;
                                        }
                                        if (alpha < 0) {
                                            alpha = 0;
                                        }
                                    }
                                }
                            }
                            if (USE_BYTE) {
                                // [0; 255]
                                alpha = (alpha + 63) / NORM_BYTE7;
                            }
                        }

                        // check alpha from table:
                        final int diff = Math.abs(alpha - approx_alpha);

                        if (diff > 1) {
                            if (false) {
                                System.out.println("diff correction (ls = " + ixls + " ld = " + ixld + ") (sls = " + sls + " sld = " + sld
                                        + " (real_alpha = " + alpha + " alpha = " + approx_alpha + "): " + diff);
                            }
                            nd++;
                            rms += diff;
                            max = Math.max(diff, max);
                            rmsTable[ixls][ixld] += diff;
                            maxTable[ixls][ixld] = Math.max(diff, maxTable[ixls][ixld]);
                        }
                    }
                }
            }

            System.out.println("nLuma: " + nLuma + "(th= " + L_THRESHOLD + "): total rms (N=" + nd + ") = " + rms + " max = " + max);
            /*
                nLuma:  27(th= 0.040): total rms (N=852 318) = 2 021 172 max = 6
                nLuma:  32(th= 0.034): total rms (N=662 334) = 1 494 872 max = 6      993 tables (1Mb)
                nLuma:  36(th= 0.030): total rms (N=498 736) = 1 078 144 max = 5
                nLuma:  55(th= 0.020): total rms (N=167 312) =   339 964 max = 3
                nLuma: 110(th= 0.010): total rms (N=  2 088) =     4 182 max = 3    11991 tables (12Mb)
             */

            if (false) {
                for (int ixls = 0; ixls < nLuma; ixls++) {
                    int total = 0;
                    for (int ixld = ixls; ixld < nLuma; ixld++) {
                        total += rmsTable[ixls][ixld];
                        // symetric: only show triangle
                        if (ixld >= ixls) {
                            System.out.println("RMS[ls = " + ixls + "][ld = " + ixld + "] : " + rmsTable[ixls][ixld]
                                    + " max = " + maxTable[ixls][ixld]);
                        }
                    }
                    System.out.println("TOTAL[ls = " + ixls + "] : " + total);
                }
            }
        }
        // System.out.println("AlphaLUT: build duration= " + (1e-6 * (System.nanoTime() - start)) + " ms.");
    }
}
