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
import static org.marlin.pipe.MarlinCompositor.BLEND_CONTRAST;

import static org.marlin.pipe.MarlinCompositor.GAMMA;
import static org.marlin.pipe.MarlinCompositor.GAMMA_sRGB;
import static org.marlin.pipe.MarlinCompositor.Y_to_L;

public final class BlendComposite {

    protected final static boolean FIX_LUM = false;
    protected final static boolean FIX_CONTRAST = true;
    protected final static boolean LUMA_Y = false;
    protected final static boolean USE_Y_TO_L = true;

    protected final static int TILE_WIDTH = 128;

    protected final static int NORM_BYTE = 0xFF; // 255
    protected final static int NORM_BYTE7 = 0x7F; // 127
    protected final static int NORM_ALPHA = (NORM_BYTE * NORM_BYTE7); // 32385 = 255 x 127
    protected final static int MASK_ALPHA = (1 << (8 + 7)) - 1; // 32767

    protected final static BlendComposite.GammaLUT GAMMA_LUT = new BlendComposite.GammaLUT(GAMMA);

    protected final static int CONTRAST = (int) Math.round(255f * BLEND_CONTRAST); // [0; 255]

    protected final static BlendComposite.GammaLUT Y2L_LUT = new BlendComposite.GammaLUT(Y_to_L, NORM_ALPHA);

    // OLD approach:
    protected final static double BRIGHTNESS_GAMMA = 0.43;
    protected final static BlendComposite.GammaLUT brightness_LUT = (FIX_LUM) ? new BlendComposite.GammaLUT(BRIGHTNESS_GAMMA, NORM_ALPHA) : null;

    private final static BlendComposite BLEND_SRC_OVER_NO_EXTRA_ALPHA
                                        = new BlendComposite(BlendComposite.BlendingMode.SRC_OVER, 1f);

    static {
        System.out.println("INFO: Marlin Compositor advanced blending mode: " + getBlendingMode());
    }

    public static String getBlendingMode() {
        return "gamma_" + ((GAMMA == GAMMA_sRGB) ? "sRGB" : GAMMA)
                + ((FIX_LUM) ? "_lum" : "")
                + ((FIX_CONTRAST) ? ("_contrast_" + BLEND_CONTRAST) : "")
                + ((LUMA_Y) ? "_lumaY" : "_lumaGray")
                + ((USE_Y_TO_L) ? "_YtoL" : "");
    }

    final static class GammaLUT {

        // TODO: use Unsafe (off-heap table)
        final int[] dir;
        final int[] dirL;
        final int[] inv;

        GammaLUT(final double gamma) {
            this(gamma, NORM_BYTE);
        }

        GammaLUT(final double gamma, final int range) {
            dir = new int[range + 1];
            dirL = new int[NORM_ALPHA + 1];
            inv = new int[MASK_ALPHA + 1]; // larger to use bit masking

            double max, scale;

            // [0; 255] to [0; 32385]
            max = (double) range;
            scale = (double) NORM_ALPHA;

            if (gamma == Y_to_L) {
                // Y -> L
                for (int i = 0; i <= range; i++) {
                    dir[i] = (int) Math.round(scale * Y_to_L(i / max));
                    // System.out.println("Y_to_L[" + i + "] = " + dir[i]);
                }
            } else if (gamma == GAMMA_sRGB) {
                // sRGB -> RGB
                for (int i = 0; i <= range; i++) {
                    dir[i] = (int) Math.round(scale * sRGB_to_RGB(i / max));
                    // System.out.println("sRGB_to_RGB[" + i + "] = " + dir[i]);
                }
            } else {
                for (int i = 0; i <= range; i++) {
                    dir[i] = (int) Math.round(scale * Math.pow(i / max, gamma));
                    // System.out.println("dir[" + i + "] = " + dir[i]);
                }
            }

            // [0; 32385] to [0; 32385]
            max = (double) NORM_ALPHA;
            scale = (double) NORM_ALPHA;

            if (gamma == Y_to_L) {
                // Y -> L
                for (int i = 0; i <= NORM_ALPHA; i++) {
                    dirL[i] = (int) Math.round(scale * Y_to_L(i / max));
                    // System.out.println("Y_to_L[" + i + "] = " + dirL[i]);
                }
            } else if (gamma == GAMMA_sRGB) {
                // sRGB -> RGB
                for (int i = 0; i <= NORM_ALPHA; i++) {
                    dirL[i] = (int) Math.round(scale * sRGB_to_RGB(i / max));
                    // System.out.println("sRGB_to_RGB[" + i + "] = " + dirL[i]);
                }
            } else {
                for (int i = 0; i <= NORM_ALPHA; i++) {
                    dirL[i] = (int) Math.round(scale * Math.pow(i / max, gamma));
                    // System.out.println("dir[" + i + "] = " + dirL[i]);
                }
            }

            // [0; 32385] to [0; 255]
            max = (double) NORM_ALPHA;
            scale = (double) range;
            if (gamma == Y_to_L) {
                // L -> Y
                // not implemented yet (not used) !
            } else if (gamma == GAMMA_sRGB) {
                // RGB Linear -> sRGB
                for (int i = 0; i <= NORM_ALPHA; i++) {
                    inv[i] = (int) Math.round(scale * RGB_to_sRGB(i / max));
                    // System.out.println("inv[" + i + "] = " + inv[i]);
                }
            } else {
                final double invGamma = 1.0 / gamma;

                for (int i = 0; i <= NORM_ALPHA; i++) {
                    inv[i] = (int) Math.round(scale * Math.pow(i / max, invGamma));
                    // System.out.println("inv[" + i + "] = " + inv[i]);
                }
            }
        }
    }

    public enum BlendingMode {

        SRC_OVER
    }

    /* members */
    final BlendComposite.BlendingMode mode;
    final float extraAlpha;

    private BlendComposite(final BlendComposite.BlendingMode mode, final float extraAlpha) {
        this.mode = mode;
        this.extraAlpha = extraAlpha;
    }

    static BlendComposite getInstance(final BlendComposite.BlendingMode mode, final float extraAlpha) {
        if (extraAlpha == 1f) {
            return BLEND_SRC_OVER_NO_EXTRA_ALPHA;
        }
        // System.out.println("getInstance(mode: " + mode + " extraAlpha:" + extraAlpha + ")");
        return new BlendComposite(mode, extraAlpha);
    }

    BlendComposite.BlendingMode getMode() {
        return mode;
    }

    /**
     * Returns the alpha value of this <code>AlphaComposite</code>.  If this
     * <code>AlphaComposite</code> does not have an alpha value, 1.0 is returned.
     * @return the alpha value of this <code>AlphaComposite</code>.
     */
    float getAlpha() {
        return extraAlpha;
    }

    boolean hasExtraAlpha() {
        return this.extraAlpha != 1f;
    }

    // TODO: use 1 compositor context
    static abstract class BlendingContext {

        /* members */
        protected int _extraAlpha;
        protected BlendComposite.Blender _blender;

        BlendingContext() {
            // System.out.println("new BlendingContext: "+getClass().getSimpleName());
        }

        final BlendComposite.BlendingContext init(final BlendComposite composite) {
            this._blender = BlendComposite.Blender.getBlenderFor(composite);
            this._extraAlpha = Math.round(127f * composite.extraAlpha); // [0; 127] ie 7 bits
            return this; // fluent API
        }

        abstract void compose(final int srcRGBA, final Raster srcIn,
                              final WritableRaster dstOut,
                              final int x, final int y, final int w, final int h,
                              final byte[] mask, final int maskoff, final int maskscan);
    }

    static abstract class Blender {

        private final static BlenderSrcOver srcOverBlender = new BlenderSrcOver();

        // fs and fs are in [0; 255] (8bits)
        public abstract void blend(int[] src, int[] dst, final int fs, final int fd, int[] result);

        public static BlendComposite.Blender getBlenderFor(BlendComposite composite) {
            switch (composite.getMode()) {
                case SRC_OVER:
                    return srcOverBlender;
                default:
                    throw new IllegalArgumentException("Blender not implement for " + composite.getMode().name());
            }
        }
    }

    final static class BlenderSrcOver extends BlendComposite.Blender {

        @Override
        public void blend(final int[] src, final int[] dst,
                          final int fs, final int fd,
                          final int[] result) {
            // src & dst are gamma corrected and premultiplied by their alpha values:

            // ALPHA in [0; 255] so divide by 255 (not shift):
// BAD EQUATIONS
            // color components in range [0; 32385]
            result[0] = (src[0] * fs + dst[0] * fd) / NORM_BYTE;
            result[1] = (src[1] * fs + dst[1] * fd) / NORM_BYTE;
            result[2] = (src[2] * fs + dst[2] * fd) / NORM_BYTE;
            // alpha in range [0; 255]
            result[3] = fs + fd;
        }
    }

    private static double RGB_to_sRGB(final double c) {
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

    private static double sRGB_to_RGB(final double c) {
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

    private static double Y_to_L(final double Y) {
        // http://brucelindbloom.com/index.html?Eqn_RGB_to_XYZ.html
        if (Y > 0.008856452) {
            final double y = (Y + 0.16) / 1.16;
            return y * y * y;
        }
        return Y / 0.9033;
    }

    static int luminance(final int r, final int g, final int b) {
        // Y
        // from RGB to XYZ:
        // http://brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html
        /* sRGB (D65):
        [XYZ] = [M] [RGB linear] 
     0.4124564  0.3575761  0.1804375 
[M]= 0.2126729  0.7151522  0.0721750
     0.0193339  0.1191920  0.9503041
         */
        if (LUMA_Y) {
            // luminance means Y:
            return (r * 13938 + g * 46868 + b * 4730) >> 16;
        }
        return (r + g + b) / 3;
    }

    // Lum bits = 4 (16 levels is enough)
    protected final static int LUM_BITS = 4;
    protected final static int LUM_MAX = (1 << LUM_BITS) - 1; // 4b - 1

    static int luminance4b(final int r, final int g, final int b) {
        // r/g/b in [0; 32385] ie 255 * 127 ie 8+7 ~ 15 bits
        // Y
        // from RGB to XYZ:
        // http://brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html
        /* sRGB (D65):
        [XYZ] = [M] [RGB linear] 
     0.4124564  0.3575761  0.1804375 
[M]= 0.2126729  0.7151522  0.0721750
     0.0193339  0.1191920  0.9503041
         */
        if (LUMA_Y) {
            // luminance means Y:
            return (r * 13938 + g * 46868 + b * 4730) >> 28; // / 16b / 15-3 = 12 bits = 28 bits
            //  + (1 << 15)
        }
        return ((r + g + b) / 3) >> 12;
    }
}
