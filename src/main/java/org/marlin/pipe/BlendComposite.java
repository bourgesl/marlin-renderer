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
import static org.marlin.pipe.MarlinCompositor.BLEND_MODE;
import static org.marlin.pipe.MarlinCompositor.BLEND_QUALITY;
import static org.marlin.pipe.MarlinCompositor.BLEND_SPEED;
import static org.marlin.pipe.MarlinCompositor.FIX_CONTRAST;
import static org.marlin.pipe.MarlinCompositor.FIX_LUM;

import static org.marlin.pipe.MarlinCompositor.GAMMA;
import static org.marlin.pipe.MarlinCompositor.GAMMA_L_to_Y;
import static org.marlin.pipe.MarlinCompositor.GAMMA_sRGB;
import static org.marlin.pipe.MarlinCompositor.IS_LINEAR;
import static org.marlin.pipe.MarlinCompositor.LUMA_GAMMA;
import static org.marlin.pipe.MarlinCompositor.USE_CONTRAST_L;
import sun.java2d.SunGraphics2D;

public final class BlendComposite {

    protected final static int TILE_WIDTH = 128;

    public final static int NORM_BYTE = 0xFF; // 255
    public final static int NORM_BYTE7 = 0x7F; // 127
    public final static int NORM_ALPHA = (NORM_BYTE * NORM_BYTE7); // 32385 = 255 x 127
    public final static int MASK_ALPHA = (1 << (8 + 7)) - 1; // 32767

    // contrast scale in [0; 255]
    protected final static int CONTRAST = (int) Math.round(255f * BLEND_CONTRAST); // [0; 255]

    // Device Gamma LUT:
    public final static GammaLUT GAMMA_LUT = new GammaLUT(GAMMA, false);

    // Luma (gamma) LUT:
    public final static GammaLUT LUMA_LUT = new GammaLUT(LUMA_GAMMA, true, FIX_LUM || BLEND_QUALITY);

    static {
        System.out.println("INFO: Marlin Compositor advanced blending mode: " + getBlendingMode());
    }

    public static String getBlendingMode() {
        return "gamma_" + ((GAMMA == GAMMA_sRGB) ? "sRGB" : GAMMA)
                + ((BLEND_SPEED) ? "_speed" : ((BLEND_QUALITY) ? "_qual" : ""))
                + "_mode_" + ((IS_LINEAR) ? "linear" : BLEND_MODE)
                + ((FIX_LUM) ? "_lum" : (FIX_CONTRAST ? (USE_CONTRAST_L ? "_contrastL" : "_contrast") : ""))
                + "_" + BLEND_CONTRAST
                + "_lumaY"
                + "_lerp_" + ((LUMA_GAMMA == GAMMA_L_to_Y) ? "L-Y" : ((LUMA_GAMMA == GAMMA_sRGB) ? "sRGB" : LUMA_GAMMA));
    }

    public enum BlendingMode {

        SRC_OVER
    }

    /* members */
    BlendComposite.BlendingMode mode;
    float extraAlpha;

    BlendComposite() {
        this(BlendComposite.BlendingMode.SRC_OVER, 1f);
    }

    BlendComposite(final BlendComposite.BlendingMode mode, final float extraAlpha) {
        set(mode, extraAlpha);
    }

    void set(final BlendComposite.BlendingMode mode, final float extraAlpha) {
        this.mode = mode;
        this.extraAlpha = extraAlpha;
    }

    static BlendComposite getInstance(final CompositorContext ctx, final BlendComposite.BlendingMode mode, final float extraAlpha) {
        final BlendComposite composite = ctx.getComposite();
        composite.set(mode, extraAlpha);
        return composite;
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

        protected final static boolean USE_LONG = true;

        protected final static boolean USE_PREV_RES = true; // higher probability (no info on density ie typical alpha ?

        /* members */
        protected int _extraAlpha;
        protected BlendComposite.Blender _blender = null;

        BlendingContext() {
            // System.out.println("new BlendingContext: "+getClass().getSimpleName());
        }

        BlendComposite.BlendingContext init(final BlendComposite composite, final SunGraphics2D sg) {
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
        // luminance means Y:
        return (r * 13938 + g * 46868 + b * 4730) >> 16;
    }

    static int luminance10b(final int r, final int g, final int b) {
        // r/g/b in [0; 32385] ie 255 * 127 ie 8+7 ~ 15 bits        
        // scale down [0; 32385] to [0; 1023]:

        /* sRGB (D65):
        [Y] = [0.2126729  0.7151522  0.0721750] [RGB linear]     
         */
        // luminance means Y:
        return (r * 881 + g * 2961 + b * 299 + 65535) >> 17;
    }

    public static void main(String[] args) {
        System.out.println("luminance10b(0,0,0): " + luminance10b(0, 0, 0));
        System.out.println("luminance10b(32385,32385,32385): " + luminance10b(32385, 32385, 32385));
    }
}
