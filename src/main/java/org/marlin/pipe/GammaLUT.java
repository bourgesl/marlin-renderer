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

import static org.marlin.pipe.BlendComposite.MASK_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.MarlinCompositor.GAMMA_L_to_Y;
import static org.marlin.pipe.MarlinCompositor.GAMMA_sRGB;
import sun.misc.Unsafe;

public final class GammaLUT {

    // TODO: use Unsafe (off-heap table)
    final double gamma;
    final int range;
    final int[] dir;
    final int[] inv;

    final OffHeapArray LUT_UNSAFE_dir;
    final OffHeapArray LUT_UNSAFE_inv;

    GammaLUT(final double gamma, final boolean fullRange) {
        this(gamma, fullRange, true);
    }

    GammaLUT(final double gamma, final boolean fullRange, final boolean prepareTables) {
        this.gamma = gamma;
        this.range = (fullRange) ? NORM_ALPHA : NORM_BYTE;

        if (prepareTables) {
            this.dir = new int[range + 1];

            for (int i = 0; i <= range; i++) {
                dir[i] = (int) Math.round(dir(i));
            }

            this.inv = new int[MASK_ALPHA + 1]; // larger to use bit masking

            for (int i = 0; i <= NORM_ALPHA; i++) {
                inv[i] = (int) Math.round(inv(i));
            }

            LUT_UNSAFE_dir = new OffHeapArray(this, dir.length << 2L); // int
            LUT_UNSAFE_inv = new OffHeapArray(this, inv.length << 2L); // int

            final Unsafe _unsafe = OffHeapArray.UNSAFE;
            final long addr_dir = LUT_UNSAFE_dir.start;

            for (int i = 0; i < dir.length; i++) {
                _unsafe.putInt(addr_dir + (i << 2L), dir[i]);
            }

            final long addr_inv = LUT_UNSAFE_inv.start;

            for (int i = 0; i < inv.length; i++) {
                _unsafe.putInt(addr_inv + (i << 2L), inv[i]);
            }
        } else {
            this.dir = null;
            this.inv = null;
            this.LUT_UNSAFE_dir = null;
            this.LUT_UNSAFE_inv = null;
        }
    }

    public double dir(final double i) {
        // Luma LUT is full range and should not use sRGB or Y to L profiles:
        if (gamma == GAMMA_L_to_Y) {
            // Y -> L (useless)
            return NORM_ALPHA * L_to_Y(i / ((double) range));
        } else if (gamma == GAMMA_sRGB) {
            // sRGB -> RGB
            return NORM_ALPHA * sRGB_to_RGB(i / ((double) range));
        }
        return NORM_ALPHA * Math.pow(i / ((double) range), gamma);
    }

    public double inv(final double i) {
        if (gamma == GAMMA_L_to_Y) {
            // Y -> L (useless)
            return range * Y_to_L(i / ((double) NORM_ALPHA));
        } else if (gamma == GAMMA_sRGB) {
            // sRGB -> RGB
            return range * RGB_to_sRGB(i / ((double) NORM_ALPHA));
        }
        return range * Math.pow(i / ((double) NORM_ALPHA), 1.0 / gamma);
    }

    public static double RGB_to_sRGB(final double c) {
        if (c <= 0.0) {
            return 0.0;
        }
        if (c >= 1.0) {
            return 1.0;
        }
        if (c <= 0.0031308) {
            return c * 12.92;
        } else {
            return 1.055 * Math.pow(c, 1.0 / GAMMA_sRGB) - 0.055;
        }
    }

    public static double sRGB_to_RGB(final double c) {
        // Convert non-linear RGB coordinates to linear ones,
        //  numbers from the w3 spec.
        if (c <= 0.0) {
            return 0.0;
        }
        if (c >= 1.0) {
            return 1.0;
        }
        if (c <= 0.04045) {
            return c / 12.92;
        } else {
            return Math.pow((c + 0.055) / 1.055, GAMMA_sRGB);
        }
    }

    public static double L_to_Y(final double L) {
        // http://brucelindbloom.com/index.html?Eqn_RGB_to_XYZ.html
        if (L > 0.08) {
            final double l = (L + 0.16) / 1.16;
            return l * l * l;
        }
        return L / 9.033;
    }

    public static double Y_to_L(final double Y) {
        // http://brucelindbloom.com/index.html?Eqn_RGB_to_XYZ.html
        // 0.08 / 9.033 = 0.008856415
        if (Y > 0.008856415) {
            return 1.16 * Math.cbrt(Y) - 0.16;
        }
        return Y * 9.033;
    }
}
