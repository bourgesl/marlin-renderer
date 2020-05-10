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

import static org.marlin.pipe.MarlinCompositor.GAMMA;

public final class BlendComposite {

    public final static int TILE_WIDTH = 128;

    public final static BlendComposite.GammaLUT GAMMA_LUT = new BlendComposite.GammaLUT(GAMMA);

    public final static int NORM_BYTE = 0xFF; // 255
    public final static int NORM_BYTE7 = 0x7F; // 127
    public final static int NORM_ALPHA = (NORM_BYTE * NORM_BYTE7); // 32385 = 255 x 127
    public final static int NORM_GAMMA = (1 << 15) - 1; // 32767

    private final static BlendComposite BLEND_SRC_OVER_NO_EXTRA_ALPHA
                                        = new BlendComposite(BlendComposite.BlendingMode.SRC_OVER, 1f);

    public static double getCompatibleCoverageFactor(final double origPixelCov) {
        if (origPixelCov == 0.0) {
            return 1.0;
        }
        return (1.0 * GAMMA_LUT.inv[(int) (NORM_GAMMA * origPixelCov)]) / (NORM_BYTE * origPixelCov);
    }

    public static String getBlendingMode() {
        return "_gam_" + GAMMA;
    }

    static class GammaLUT {

        final int[] dir = new int[NORM_BYTE + 1];
        final int[] inv = new int[NORM_GAMMA + 1];

        GammaLUT(final double gamma) {
            final double invGamma = 1.0 / gamma;
            double max, scale;

            // [0; 255] to [0; 32767]
            max = (double) NORM_BYTE;
            scale = (double) NORM_GAMMA;

            for (int i = 0; i <= NORM_BYTE; i++) {
                dir[i] = (int) Math.round(scale * Math.pow(i / max, gamma));
                // System.out.println("dir[" + i + "] = " + dir[i]);
            }

            // [0; 32767] to [0; 255]
            max = (double) NORM_GAMMA;
            scale = (double) NORM_BYTE;

            for (int i = 0; i <= NORM_GAMMA; i++) {
                inv[i] = (int) Math.round(scale * Math.pow(i / max, invGamma));
                // System.out.println("inv[" + i + "] = " + inv[i]);
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

        BlendingContext() {
            // System.out.println("new BlendingContext: "+getClass().getSimpleName());
        }

        abstract BlendingContext init(final BlendComposite composite);

        abstract void compose(final int srcRGBA, final Raster srcIn,
                              final byte[] atile, final int offset, final int tilesize,
                              final WritableRaster dstOut,
                              final int w, final int h);
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
            // color components in range [0; 32767]
            result[0] = (src[0] * fs + dst[0] * fd) / NORM_BYTE;
            result[1] = (src[1] * fs + dst[1] * fd) / NORM_BYTE;
            result[2] = (src[2] * fs + dst[2] * fd) / NORM_BYTE;
            // alpha in range [0; 255]
            result[3] = fs + fd;
        }
    }
}
