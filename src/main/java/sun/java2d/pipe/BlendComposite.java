/**
 * TODO: Fix class header
 */
package sun.java2d.pipe;

import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

public final class BlendComposite implements Composite {

    private final static double GAMMA = 2.2;
    private final static BlendComposite.GammaLUT gamma_LUT = new BlendComposite.GammaLUT(GAMMA);

    private static boolean DEBUG = false;
    private static boolean TRACE = false;
    private final static boolean USE_COLORSPACE = false;
    private final static boolean USE_LAB = false;
    private final static boolean USE_MIX_L = true;

    public static String getBlendingMode() {
        if (USE_COLORSPACE) {
            return "_CIE_" + ((USE_LAB) ? "Lab" : "Lch") + ((USE_MIX_L) ? "_mixL" : "_mixY");
        }
        return "_gam_" + GAMMA;
    }

    public static class GammaLUT {

        private final static int MAX_COLORS = 256;
        private final int[] dir = new int[MAX_COLORS];
        private final int[] inv = new int[MAX_COLORS];

        GammaLUT(final double gamma) {
            final double max = (double) (MAX_COLORS - 1);
            final double invGamma = 1.0 / gamma;

            for (int i = 0; i < MAX_COLORS; i++) {
                // TODO: use fromRGB() and toRGB() more precise
                // TODO: use 16 bits integer maths
//                dir[i] = (int) (max * Math.pow(i / max, gamma));
//                inv[i] = (int) (max * Math.pow(i / max, invGamma));
                dir[i] = (int) (max * Math.pow(i / max, gamma));
                inv[i] = (int) (max * Math.pow(i / max, invGamma));
//                System.out.println("dir[" + i + "] = " + dir[i]);
//                System.out.println("inv[" + i + "] = " + inv[i]);
            }
        }
    }

    static {
        if (DEBUG) {
            TRACE = true;
            final int rgba_1 = Color.white.getRGB();
            final int rgba_2 = Color.black.getRGB();

            float[] rgb = new float[4];
            float[] src = new float[4];
            float[] dst = new float[4];
            float[] mix = new float[4];

            /*
             ColorSpace cs_sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);

             CIELabColorSpace labCS = new CIELabColorSpace(CIELabColorSpace.getD50WhitePoint());

             Color.white.getRGBComponents(rgb);
             float[] src2 = labCS.fromRGB(rgb);

             System.out.println("src: " + Arrays.toString(src2) + " = " + Arrays.toString(rgb));

             */
            src = (USE_LAB) ? sRGB_to_Lab(rgba_1, src) : sRGB_to_LCH(rgba_1, src);
            dst = (USE_LAB) ? sRGB_to_Lab(rgba_2, dst) : sRGB_to_LCH(rgba_2, dst);

            System.out.println("src: " + Arrays.toString(src) + " = " + Arrays.toString(sRGB_to_f(rgba_1, rgb)));
            System.out.println("dst: " + Arrays.toString(dst) + " = " + Arrays.toString(sRGB_to_f(rgba_2, rgb)));

            System.out.println("sRGB(for rgb=0.5): " + RGB_to_sRGBi(0.5f));

            for (int i = 0; i <= 256; i += 16) {

                float src_alpha = (i / 255f);

                // src & dst are Lab or LCH:
                if (USE_MIX_L) {
                    mix[0] = (dst[0] + src_alpha * (src[0] - dst[0]));
                } else {
                    // L is luminance, use Y (brightness) instead:
                    float Ysrc = L_to_Y(src[0]);
                    float Ydst = L_to_Y(dst[0]);
                    mix[0] = Y_to_L(Ydst + src_alpha * (Ysrc - Ydst));
                }

                // a(Lab) or C(LCH):
                mix[1] = (dst[1] + src_alpha * (src[1] - dst[1]));

                if (USE_LAB) {
                    // b(Lab)
                    mix[2] = (dst[2] + src_alpha * (src[2] - dst[2]));
                } else {
                    // H(Lch) angle combination:
                    float d = src[2] - dst[2];
                    if (d > 180f) {
                        d -= 360f;
                    } else if (d < -180f) {
                        d += 360d;
                    }
                    mix[2] = (dst[2] + src_alpha * d);
                }
                mix[3] = 1f;

                if (TRACE) {
                    System.out.println("mixLCH: " + Arrays.toString(mix));
                }

                System.out.println("alpha[ " + i + "] = " + Arrays.toString(mix));

                int rgba = (USE_LAB) ? Lab_to_sRGB(mix) : LCH_to_sRGB(mix);

                System.out.println("alpha[ " + i + "] RGB = " + Arrays.toString(sRGB_to_f(rgba, rgb)));
            }
            TRACE = false;
        }
    }

    public enum BlendingMode {

        SRC_OVER
    }
    public static final BlendComposite SrcOver = new BlendComposite(BlendComposite.BlendingMode.SRC_OVER);
    private BlendComposite.BlendingMode mode;

    private BlendComposite(BlendComposite.BlendingMode mode) {
        this.mode = mode;
    }

    public static BlendComposite getInstance(BlendComposite.BlendingMode mode) {
        return new BlendComposite(mode);
    }

    public BlendComposite.BlendingMode getMode() {
        return mode;
    }

    @Override
    public int hashCode() {
        return mode.ordinal();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlendComposite)) {
            return false;
        }

        BlendComposite bc = (BlendComposite) obj;

        return (mode == bc.mode);
    }

    public CompositeContext createContext(ColorModel srcColorModel,
                                          ColorModel dstColorModel, RenderingHints hints) {

        // use ThreadLocal (to reduce memory footprint):
        final BlendingContext bc = blendContextThreadLocal.get();
        bc.init(this);
        return bc;

    }
    /**
     * Per-thread BlendingContext (very small so do not use any Soft or Weak Reference)
     */
    private static final ThreadLocal<BlendingContext> blendContextThreadLocal = new ThreadLocal<BlendingContext>() {
        @Override
        protected BlendingContext initialValue() {
            return new BlendingContext();
        }
    };

    private static final class BlendingContext implements CompositeContext {

        private BlendComposite.Blender _blender;
        // recycled arrays into context (shared):
        private final int[] _srcPixel = new int[4];
        private final int[] _dstPixel = new int[4];
        private final int[] _result = new int[4];
        private int[] _srcPixels = new int[32];
        private int[] _dstPixels = new int[32];
        private int[] _maskPixels = new int[32];

        BlendingContext() {
            // ThreadLocal constructor
        }

        void init(BlendComposite composite) {
            this._blender = BlendComposite.Blender.getBlenderFor(composite);
        }

        int[] getSrcPixels(final int len) {
            int[] t = _srcPixels;
            if (t.length < len) {
                System.out.println("_srcPixels = new int[" + len + "]");
                // create a larger stride and may free current maskStride (too small)
                _srcPixels = t = new int[len];
            }
            return t;
        }

        int[] getDstPixels(final int len) {
            int[] t = _dstPixels;
            if (t.length < len) {
                System.out.println("_dstPixels = new int[" + len + "]");
                // create a larger stride and may free current maskStride (too small)
                _dstPixels = t = new int[len];
            }
            return t;
        }

        int[] getMaskPixels(final int len) {
            int[] t = _maskPixels;
            if (t.length < len) {
                System.out.println("_maskPixels = new int[" + len + "]");
                // create a larger stride and may free current maskStride (too small)
                _maskPixels = t = new int[len];
            }
            return t;
        }

        public void dispose() {
        }

        public void compose(Raster srcIn, Raster dstIn, WritableRaster dstOut) {
            if (srcIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT
                    || dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT
                    || dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
                throw new IllegalStateException(
                        "Source and destination must store pixels as INT.");
            }
            /*
             System.out.println("src = " + src.getBounds());
             System.out.println("dstIn = " + dstIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
             */
            final int width = Math.min(srcIn.getWidth(), dstIn.getWidth());
            final int height = Math.min(srcIn.getHeight(), dstIn.getHeight());

            final int[] gamma_dir = gamma_LUT.dir;
            final int[] gamma_inv = gamma_LUT.inv;

            final BlendComposite.Blender blender = _blender;

            // use shared arrays:
            final int[] srcPixel = _srcPixel;
            final int[] dstPixel = _dstPixel;
            final int[] result = _result;

            final int[] srcPixels = getSrcPixels(width);
            final int[] dstPixels = getDstPixels(width);
            final int[] maskPixels = getMaskPixels(width);

            float[] src = new float[4];
            float[] dst = new float[4];
            float[] mix = new float[4];

            int alpha, pixel;
            float src_alpha;

            for (int y = 0; y < height; y++) {
                // TODO: use directly BufferInt
                srcIn.getDataElements(0, y, width, 1, srcPixels);
                dstIn.getDataElements(0, y, width, 1, dstPixels);
                dstOut.getDataElements(0, y, width, 1, maskPixels);

                for (int x = 0; x < width; x++) {
                    // pixels are stored as INT_ARGB
                    // our arrays are [R, G, B, A]
                    pixel = maskPixels[x];
                    alpha = /* ( */ pixel /* >> 24) & 0xFF */;

                    if (alpha == 255) {
                        dstPixels[x] = srcPixels[x];
                    } else if (alpha != 0) {
//                        System.out.println("alpha = " + alpha);

                        if (USE_COLORSPACE) {
                            src = (USE_LAB) ? sRGB_to_Lab(srcPixels[x], src) : sRGB_to_LCH(srcPixels[x], src);
                            dst = (USE_LAB) ? sRGB_to_Lab(dstPixels[x], dst) : sRGB_to_LCH(dstPixels[x], dst);

                            if (TRACE) {
                                System.out.println("src: " + Arrays.toString(src));
                                System.out.println("dst: " + Arrays.toString(dst));
                            }

                            src_alpha = (alpha / 255f);

                            // src & dst are Lab or LCH:
                            if (USE_MIX_L) {
                                mix[0] = (dst[0] + src_alpha * (src[0] - dst[0]));
                            } else {
                                // L is luminance, use Y (brightness) instead:
                                float Ysrc = L_to_Y(src[0]);
                                float Ydst = L_to_Y(dst[0]);
                                mix[0] = Y_to_L(Ydst + src_alpha * (Ysrc - Ydst));
                            }

                            // a(Lab) or C(LCH):
                            mix[1] = (dst[1] + src_alpha * (src[1] - dst[1]));

                            if (USE_LAB) {
                                // b(Lab)
                                mix[2] = (dst[2] + src_alpha * (src[2] - dst[2]));
                            } else {
                                // H(Lch) angle combination:
                                float d = src[2] - dst[2];
                                if (d > 180f) {
                                    d -= 360f;
                                } else if (d < -180f) {
                                    d += 360d;
                                }
                                mix[2] = (dst[2] + src_alpha * d);
                            }
                            mix[3] = 1f;

                            if (TRACE) {
                                System.out.println("mixLCH: " + Arrays.toString(mix));
                            }

                            dstPixels[x] = (USE_LAB) ? Lab_to_sRGB(mix) : LCH_to_sRGB(mix);

                        } else {

                            // blend
                            pixel = srcPixels[x];
                            srcPixel[0] = gamma_dir[(pixel >> 16) & 0xFF];
                            srcPixel[1] = gamma_dir[(pixel >> 8) & 0xFF];
                            srcPixel[2] = gamma_dir[(pixel) & 0xFF];
                            srcPixel[3] = (pixel >> 24) & 0xFF;

                            pixel = dstPixels[x];
                            dstPixel[0] = gamma_dir[(pixel >> 16) & 0xFF];
                            dstPixel[1] = gamma_dir[(pixel >> 8) & 0xFF];
                            dstPixel[2] = gamma_dir[(pixel) & 0xFF];
                            dstPixel[3] = (pixel >> 24) & 0xFF;

                            // recycle int[] instances:
                            blender.blend(srcPixel, dstPixel, alpha, result);

                            // mixes the result with the opacity
                            dstPixels[x] = (/*result[3] & */0xFF) << 24
                                    | gamma_inv[result[0] & 0xFF] << 16
                                    | gamma_inv[result[1] & 0xFF] << 8
                                    | gamma_inv[result[2] & 0xFF];
                        }
                    }
                }
                dstOut.setDataElements(0, y, width, 1, dstPixels);
            }
        }
    }

    private static abstract class Blender {

        private final static BlenderSrcOver srcOverBlender = new BlenderSrcOver();

        public abstract void blend(int[] src, int[] dst, int alpha, int[] result);

        public static BlendComposite.Blender getBlenderFor(BlendComposite composite) {
            switch (composite.getMode()) {
                case SRC_OVER:
                    return srcOverBlender;
                default:
                    throw new IllegalArgumentException("Blender not implement for " + composite.getMode().name());
            }
        }
    }

    private final static class BlenderSrcOver extends BlendComposite.Blender {

        private static final boolean USE_FLOATS = false;

        @Override
        public void blend(final int[] src, final int[] dst, final int alpha, final int[] result) {
            if (USE_FLOATS) {
                final float src_alpha = alpha / 255f;
                final float comp_src_alpha = 1f - src_alpha;
                // src & dst are gamma corrected

                // TODO: use integer maths:
                result[0] = Math.min(255, (int) (src[0] * src_alpha + dst[0] * comp_src_alpha));
                result[1] = Math.min(255, (int) (src[1] * src_alpha + dst[1] * comp_src_alpha));
                result[2] = Math.min(255, (int) (src[2] * src_alpha + dst[2] * comp_src_alpha));
                //                            result[3] = 255; /* Math.max(0, Math.min(255, (int) (255f * (src_alpha + comp_src_alpha)))) */
            } else {
                final int src_alpha = alpha;
                final int comp_src_alpha = 255 - src_alpha;
                // src & dst are gamma corrected

                // TODO: use integer maths:
/*
                 result[0] = (src[0] * src_alpha + dst[0] * comp_src_alpha) / 255;
                 result[1] = (src[1] * src_alpha + dst[1] * comp_src_alpha) / 255;
                 result[2] = (src[2] * src_alpha + dst[2] * comp_src_alpha) / 255;
                 */
                result[0] = (src[0] * src_alpha + dst[0] * comp_src_alpha) >> 8;
                result[1] = (src[1] * src_alpha + dst[1] * comp_src_alpha) >> 8;
                result[2] = (src[2] * src_alpha + dst[2] * comp_src_alpha) >> 8;
                //                            result[3] = 255; /* Math.max(0, Math.min(255, (int) (255f * (src_alpha + comp_src_alpha)))) */
            }
        }
    }

    static float[] sRGB_to_Lab(final int rgba, final float[] Lab) {
        return XYZ_to_Lab(sRGB_to_XYZ(sRGB_to_f(rgba, Lab)));
    }

    static int Lab_to_sRGB(final float[] Lab) {
        return sRGB_to_i(XYZ_to_sRGB(Lab_to_XYZ(Lab)));
    }

    static float[] sRGB_to_LCH(final int rgba, final float[] LCH) {
        return Lab_to_LCH(XYZ_to_Lab(sRGB_to_XYZ(sRGB_to_f(rgba, LCH))));
    }

    static int LCH_to_sRGB(final float[] LCH) {
        return sRGB_to_i(XYZ_to_sRGB(Lab_to_XYZ(LCH_to_Lab(LCH))));
    }

    private static final double TWO_PI = 2.0 * Math.PI;

    static float[] Lab_to_LCH(float[] Lab) {
        if (TRACE) {
            System.out.println("Lab: " + Arrays.toString(Lab));
        }
        float H = (float) (Math.atan2(Lab[2], Lab[1]));

        if (H > 0) {
            H = (float) ((H / Math.PI) * 180.0);
        } else {
            H = (float) (360.0 - (Math.abs(H) / Math.PI) * 180.0);
        }

        float L = Lab[0];
        float C = (float) Math.sqrt(Lab[1] * Lab[1] + Lab[2] * Lab[2]);

        Lab[0] = L;
        Lab[1] = C;
        Lab[2] = H;
        //return (float4)(L, C, H, Lab.w);
        if (TRACE) {
            System.out.println("Lch: " + Arrays.toString(Lab));
        }
        return Lab;
    }

    static float[] LCH_to_Lab(float[] LCH) {
        if (TRACE) {
            System.out.println("LCH: " + Arrays.toString(LCH));
        }
        float L = LCH[0];
        final double angle = LCH[2] * (Math.PI / 180.0);
        float a = (float) (Math.cos(angle)) * LCH[1];
        float b = (float) (Math.sin(angle)) * LCH[1];

        LCH[0] = L;
        LCH[1] = a;
        LCH[2] = b;
        //return (float4)(L, a, b, LCH.w);
        if (TRACE) {
            System.out.println("Lab: " + Arrays.toString(LCH));
        }
        return LCH;
    }

    static float Y_to_L(float Y) {
        return 116.0f * lab_f_to(Y) - 16.0f;
    }

    static float[] XYZ_to_Lab(float[] xyz) {
        if (TRACE) {
            System.out.println("XYZ: " + Arrays.toString(xyz));
        }

        // divide by white point:
        //CIE XYZ tristimulus values of the reference white point: Observer= 2 degrees, Illuminant= D65
//        private static final float REF_X_D65 = 95.047f;
//        private static final float REF_Y_D65 = 100.000f;
//        private static final float REF_Z_D65 = 108.883f;
        xyz[0] *= (1.0f / 0.95047f);
        xyz[2] *= (1.0f / 1.08883f);
        //xyz = (xyz > (float4) 0.008856f) ? native_powr(xyz, (float4) 1.0f / 3.0f) : 7.787f * xyz + (float4) (16.0f / 116.0f);
        xyz[0] = lab_f_to(xyz[0]);
        xyz[1] = lab_f_to(xyz[1]);
        xyz[2] = lab_f_to(xyz[2]);

        float L = 116.0f * xyz[1] - 16.0f;
        float a = 500.0f * (xyz[0] - xyz[1]);
        float b = 200.0f * (xyz[1] - xyz[2]);

        xyz[0] = L;
        xyz[1] = a;
        xyz[2] = b;
        if (TRACE) {
            System.out.println("Lab: " + Arrays.toString(xyz));
        }
        return xyz;
    }

    private static float lab_f_to(final float v) {
        //xyz = (xyz > (float4) 0.008856f) ? native_powr(xyz, (float4) 1.0f / 3.0f) : 7.787f * xyz + (float4) (16.0f / 116.0f);
        return (v > 0.008856f) ? (float) Math.cbrt(v) : 7.787037f * v + (16.0f / 116.0f);
    }
    private final static float EPSILON = 0.206896551f;
    private final static float KAPPA = (24389.0f / 27.0f);

    private static float lab_f_inv(float x) {
        return (x > EPSILON) ? x * x * x : (116.0f * x - 16.0f) / KAPPA;
    }

    static float L_to_Y(float L) {
        return lab_f_inv((L + 16.0f) / 116.0f);
    }

    static float[] Lab_to_XYZ(float[] Lab) {
        if (TRACE) {
            System.out.println("Lab: " + Arrays.toString(Lab));
        }
        float y = (Lab[0] + 16.0f) / 116.0f;
        float x = Lab[1] / 500.0f + y;
        float z = y - Lab[2] / 200.0f;

//        private static final float REF_X_D65 = 95.047f;
//        private static final float REF_Y_D65 = 100.000f;
//        private static final float REF_Z_D65 = 108.883f;
        x = 0.95047f * lab_f_inv(x);
        y = lab_f_inv(y);
        z = 1.08883f * lab_f_inv(z);

//        const float4 d50 = (float4) (0.9642f, 1.0f, 0.8249f, 0.0f);
//        XYZ = d50 * lab_f_inv(XYZ);
        Lab[0] = x;
        Lab[1] = y;
        Lab[2] = z;
        if (TRACE) {
            System.out.println("XYZ: " + Arrays.toString(Lab));
        }
        return Lab;
    }

// XYZ -> sRGB matrix, D65
    static float[] XYZ_to_sRGB(float[] XYZ) {
        if (TRACE) {
            System.out.println("XYZ: " + Arrays.toString(XYZ));
        }
        /*
         float r = 3.1338561f * XYZ[0] - 1.6168667f * XYZ[1] - 0.4906146f * XYZ[2];
         float g = -0.9787684f * XYZ[0] + 1.9161415f * XYZ[1] + 0.0334540f * XYZ[2];
         float b = 0.0719453f * XYZ[0] - 0.2289914f * XYZ[1] + 1.4052427f * XYZ[2];
         */
        float r = 3.2404542f * XYZ[0] - 1.5371385f * XYZ[1] - 0.4985314f * XYZ[2];
        float g = -0.9692660f * XYZ[0] + 1.8760108f * XYZ[1] + 0.0415560f * XYZ[2];
        float b = 0.0556434f * XYZ[0] - 0.2040259f * XYZ[1] + 1.0572252f * XYZ[2];

        XYZ[0] = r;
        XYZ[1] = g;
        XYZ[2] = b;
        if (TRACE) {
            System.out.println("sRGB: " + Arrays.toString(XYZ));
        }
        return XYZ;
    }

// sRGB -> XYZ matrix, D65
    static float[] sRGB_to_XYZ(float[] sRGB) {
        if (TRACE) {
            System.out.println("sRGB: " + Arrays.toString(sRGB));
        }
        /* sRGB 	D65 */
        float x = 0.4124564f * sRGB[0] + 0.3575761f * sRGB[1] + 0.1804375f * sRGB[2];
        float y = 0.2126729f * sRGB[0] + 0.7151522f * sRGB[1] + 0.0721750f * sRGB[2];
        float z = 0.0193339f * sRGB[0] + 0.1191920f * sRGB[1] + 0.9503041f * sRGB[2];

        sRGB[0] = x;
        sRGB[1] = y;
        sRGB[2] = z;
        if (TRACE) {
            System.out.println("XYZ: " + Arrays.toString(sRGB));
        }
        return sRGB;
    }

    static float[] sRGB_to_f(final int rgba, final float[] sRGB) {
        if (TRACE) {
            System.out.println("rgba: " + rgba);
        }
        sRGB[0] = sRGBi_to_RGB((rgba >> 16) & 0xFF);
        sRGB[1] = sRGBi_to_RGB((rgba >> 8) & 0xFF);
        sRGB[2] = sRGBi_to_RGB((rgba) & 0xFF);
        sRGB[3] = ((rgba >> 24) & 0xFF) / 255f;
        if (TRACE) {
            System.out.println("sRGB: " + Arrays.toString(sRGB));
        }
        return sRGB;
    }

    static int sRGB_to_i(final float[] sRGB) {
        if (TRACE) {
            System.out.println("sRGB: " + Arrays.toString(sRGB));
        }
        int rgba = clamp(Math.round(255f * sRGB[3])) << 24
                | RGB_to_sRGBi(sRGB[0]) << 16
                | RGB_to_sRGBi(sRGB[1]) << 8
                | RGB_to_sRGBi(sRGB[2]);
        if (TRACE) {
            final double brightness = Math.sqrt(0.299 * sRGB[0] * sRGB[0] + 0.587 * sRGB[1] * sRGB[1] + 0.114 * sRGB[2] * sRGB[2]);
            System.out.println("rgba: " + rgba + " P= " + brightness);
        }
        return rgba;
    }

    static int RGB_to_sRGBi(float val) {
        int c = Math.round(255f * RGB_to_sRGB(val));
        if (TRACE) {
            System.out.println("val: " + val + " c: " + c);
        }
        return c;
    }

    static float RGB_to_sRGB(float c) {
        if (c <= 0f) {
            return 0f;
        }
        if (c >= 1f) {
            return 1f;
        }
        if (c <= 0.0031308f) {
            return c * 12.92f;
        } else {
            return 1.055f * ((float) Math.pow(c, 1.0 / 2.4)) - 0.055f;
        }
    }

    static float sRGBi_to_RGB(int val8b) {
        float c = sRGB_to_RGB(val8b / 255f);
        if (TRACE) {
            System.out.println("val: " + val8b + " c: " + c);
        }
        return c;
    }

    static float sRGB_to_RGB(float c) {
        // Convert non-linear RGB coordinates to linear ones,
        //  numbers from the w3 spec.
        if (c <= 0f) {
            return 0f;
        }
        if (c >= 1f) {
            return 1f;
        }
        if (c <= 0.04045f) {
            return c / 12.92f;
        } else {
            return (float) (Math.pow((c + 0.055f) / 1.055f, 2.4));
        }
    }

    static int clamp(final int val) {
        if (val < 0) {
            return 0;
        }
        if (val > 255) {
            return 255;
        }
        return val;
    }
}
