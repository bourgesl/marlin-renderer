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

import java.awt.Color;
import java.awt.Paint;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import static org.marlin.pipe.BlendComposite.luminance4b;
import sun.awt.image.IntegerInterleavedRaster;
import sun.java2d.SunGraphics2D;
import static sun.java2d.SunGraphics2D.PAINT_ALPHACOLOR;
import sun.misc.Unsafe;

final class BlendingContextIntARGBFastColor extends BlendComposite.BlendingContext {

    private final static boolean DO_DIVIDE = false;

    private final static boolean UNSAFE_ALPHA_LUT = false;

    BlendingContextIntARGBFastColor() {
        super();
    }

    // members
    private int _sa, _sr, _sg, _sb;
    private int[][] _src_alpha_tables = null;
    private long _at_addr_ls;

    @Override
    BlendComposite.BlendingContext init(final BlendComposite composite, final SunGraphics2D sg) {
        // this._blender = BlendComposite.Blender.getBlenderFor(composite);
        // this._extraAlpha = 0; // unsupported

        // Prepare source pixel if constant in tile:
        if (sg.paintState <= PAINT_ALPHACOLOR) {
            // use Sungraphics2D.eargb = ie extra alpha is pre-blended into SRC ALPHA:
            int pixel = sg.eargb;

            final long gam_addr_dir = BlendComposite.GAMMA_LUT.LUT_UNSAFE_dir.start;
            final long at_addr = (UNSAFE_ALPHA_LUT) ? AlphaLUT.ALPHA_LUT.LUT_UNSAFE.start : 0L;

            final int NORM_BYTE = BlendComposite.NORM_BYTE;

            final Unsafe _unsafe = OffHeapArray.UNSAFE;

            // Source pixel sRGBA:
            // sRGBA components are not pre-multiplied by alpha:
            // NOP
            // Gamma-correction to Linear RGBA: 
            // color components in range [0; 32385]
            _sa = (pixel >> 24) & NORM_BYTE;

            _sr = _unsafe.getInt(gam_addr_dir + (((pixel >> 16) & NORM_BYTE) << 2));
            _sg = _unsafe.getInt(gam_addr_dir + (((pixel >> 8) & NORM_BYTE) << 2));
            _sb = _unsafe.getInt(gam_addr_dir + (((pixel) & NORM_BYTE) << 2));

            // c_srcPixel is Gamma-corrected Linear RGBA.
            int ls = luminance4b(_sr, _sg, _sb); // Y (4bits)
            // TODO: use sa to weight luminance ls ?
            if (UNSAFE_ALPHA_LUT) {
                _at_addr_ls = at_addr + (ls << 14);
            } else {
                _src_alpha_tables = AlphaLUT.ALPHA_LUT.alphaTables[ls & 0x0F];
            }
        }
        return this; // fluent API
    }

    @Override
    void compose(final int srcRGBA, final Raster srcIn,
                 final WritableRaster dstOut,
                 final int x, final int y, final int w, final int h,
                 final byte[] mask, final int maskOff, final int maskScan) {

        // Ignore srcIn (color only)

        /*
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int[] dstBuffer = ((DataBufferInt) dstOut.getDataBuffer()).getData();

        final int dstScan = ((IntegerInterleavedRaster) dstOut).getScanlineStride();

//            final BlendComposite.Blender blender = _blender;
        final long gam_addr_dir = BlendComposite.GAMMA_LUT.LUT_UNSAFE_dir.start;
        final long gam_addr_inv = BlendComposite.GAMMA_LUT.LUT_UNSAFE_inv.start;

        final int NORM_ALPHA = BlendComposite.NORM_ALPHA;
        final int NORM_BYTE = BlendComposite.NORM_BYTE;

//        final int[][][] alpha_tables = AlphaLUT.ALPHA_LUT.alphaTables;
        int[][] src_alpha_tables = null;
        int[] dst_alpha_tables = null;

        long at_addr_ls, at_addr_ld;
        int pixel, last_out_pixel = 0, last_dst_pixel = 0;
        int sa, sr, sg, sb;
        int da = 0, dr = 0, dg = 0, db = 0;
        int am, alpha, last_alpha = 0, fs, fd;
        int ra, rr, rg, rb;

        int ld = 0;

        final Unsafe _unsafe = OffHeapArray.UNSAFE;
        long addrDst = OffHeapArray.OFF_INT + ((y * dstScan + x) << 2); // ints

        // Prepare source pixel if constant in tile:
        {
            sa = _sa;

            if (sa == 0) {
                // Invisible Source
                // result = destination (already the case)
                return;
            }

            if ((mask == null) && (sa == NORM_BYTE)) {
                // mask with full opacity
                // output = source OVER (totally)
                if (USE_LONG && (w >= 4)) {
                    final int w2 = (w >> 1);
                    final long l2_srcRGBA = (((long) srcRGBA) << 32L) + (srcRGBA & 0xFFFFFFFFL);

                    if ((w & 1) == 1) {
                        final long dstSkip = (dstScan - w + 1) << 2L; // ints

                        for (int j = h; j > 0; --j) {
                            for (int i = w2; i > 0; --i) {
                                _unsafe.putLong(dstBuffer, addrDst, l2_srcRGBA);
                                addrDst += 8L;
                            }
                            _unsafe.putInt(dstBuffer, addrDst, srcRGBA);
                            addrDst += dstSkip;
                        }
                        return;
                    }
                    // else:
                    final long dstSkip = (dstScan - w) << 2L; // ints

                    for (int j = h; j > 0; --j) {
                        for (int i = w2; i > 0; --i) {
                            _unsafe.putLong(dstBuffer, addrDst, l2_srcRGBA);
                            addrDst += 8L;
                        }
                        addrDst += dstSkip;
                    }
                    return;
                }
                // else:
                final long dstSkip = (dstScan - w) << 2L; // ints

                for (int j = h; j > 0; --j) {
                    for (int i = w; i > 0; --i) {
                        // pixels are stored as INT_ARGB
                        // our arrays are [R, G, B, A]
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, srcRGBA);
                        addrDst += 4L;
                    }
                    addrDst += dstSkip;
                }
                return;
            }

            sr = _sr;
            sg = _sg;
            sb = _sb;
            if (UNSAFE_ALPHA_LUT) {
                at_addr_ls = _at_addr_ls;
                at_addr_ld = at_addr_ls; // for ld=0 (default)
            } else {
                src_alpha_tables = _src_alpha_tables;
                dst_alpha_tables = src_alpha_tables[0]; // for ld=0 (default)
            }
        }

        final long dstSkip = (dstScan - w) << 2L; // ints

        long addrMask = OffHeapArray.OFF_BYTE + maskOff; // bytes
        final long maskSkip = maskScan; // bytes

//        final boolean checkPrev = (w >= 8) && (h >= 8); // higher probability (no info on density ie typical alpha ?
        for (int j = h; j > 0; --j) {
            for (int i = 0; i < w; i++) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
                am = (mask != null) ? (_unsafe.getByte(mask, addrMask + i) & NORM_BYTE) : NORM_BYTE;

                /*
                 * coverage = 0 means translucent: 
                 * result = destination (already the case)
                 */
                if (am != 0) {
                    // ELSE: coverage between [1;255]:

                    // srcPixel is Gamma-corrected Linear RGBA.
                    // short-cut ?
                    if ((am == NORM_BYTE) && (sa == NORM_BYTE)) {
                        // mask with full opacity
                        // output = source OVER (totally)
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, srcRGBA);
                    } else {
                        // use heuristics: enable prev checks only for large tiles (higher probability) or FILLs only ?
                        boolean prev = USE_PREV_RES; // src pixel

                        // Destination pixel:
                        {
                            // Dest pixel Linear RGBA:
                            pixel = _unsafe.getInt(dstBuffer, addrDst);

                            if (pixel != last_dst_pixel) {
                                last_dst_pixel = pixel;
                                if (USE_PREV_RES) {
                                    prev = false;
                                }

                                // Linear RGBA components are not pre-multiplied by alpha:
                                // NOP
                                // Gamma-correction on Linear RGBA: 
                                // color components in range [0; 32385]
                                da = (pixel >> 24) & NORM_BYTE; // [0; 255]

// TODO: TEST using not int by short + shift (smaller cache ?)
                                dr = _unsafe.getInt(gam_addr_dir + (((pixel >> 16) & NORM_BYTE) << 2));
                                dg = _unsafe.getInt(gam_addr_dir + (((pixel >> 8) & NORM_BYTE) << 2));
                                db = _unsafe.getInt(gam_addr_dir + (((pixel) & NORM_BYTE) << 2));

                                // in range [0; 32385] (15bits):
                                ld = luminance4b(dr, dg, db); // Y (4bits)
                                // TODO: use da to weight luminance ld ?
                                if (UNSAFE_ALPHA_LUT) {
                                    at_addr_ld = at_addr_ls + (ld << 10);
                                } else {
                                    dst_alpha_tables = src_alpha_tables[ld & 0x0F];
                                }
                            }
                        }
                        // dstPixel is Gamma-corrected Linear RGBA.

                        // fade operator:
                        // Rs = As x Coverage
                        // Apply extra alpha:
                        if (DO_DIVIDE) {
                            alpha = (sa * am) / NORM_ALPHA; // ALPHA in range [0; 255]                            
                        } else {
                            // DivideBy255(int value): return (value + 1 + (value >> 8)) >> 8;
                            // or (((x) + (((x) + 257) >> 8)) >> 8)
                            alpha = sa * am;
                            alpha = (alpha + ((alpha + 257) >> 8)) >> 8; // div 255
                        }

                        if (USE_PREV_RES && (alpha != last_alpha)) {
                            last_alpha = alpha;
                            prev = false;
                        }

                        if (prev) {
                            // same src, dst pixels and alpha:
                            // same result pixel:
                            _unsafe.putInt(dstBuffer, addrDst, last_out_pixel);
                        } else {
                            // Adjust ALPHA (linear in L*):
                            if (UNSAFE_ALPHA_LUT) {
                                alpha = _unsafe.getInt(at_addr_ld + (alpha << 2)); // ALPHA in range [0; 255]
                            } else {
                                alpha = dst_alpha_tables[alpha & NORM_BYTE]; // ALPHA in range [0; 255]
                            }

                            // Ported-Duff rules in action:
                            // R = S x fs + D x fd
                            // Src Over Dst rule:
                            // fs = Sa
                            // fd = Da x (1 - Sa)
                            // Factors in range [0; 255]
                            fs = (alpha); // alpha

                            // fix da -> alpha:
                            if (DO_DIVIDE) {
                                fd = (da * (NORM_BYTE - alpha)) / NORM_BYTE; // TODO: avoid divide and use [0; 255]
                            } else {
                                fd = da * (NORM_BYTE - alpha);
                                fd = (fd + ((fd + 257) >> 8)) >> 8; // div 255
                            }

//                              blender.blend(srcPixel, dstPixel, fs, fd, result);
                            // ALPHA in range [0; 255]
                            alpha = fs + fd;

                            if (alpha == 0) {
                                // output = none
                                // Source pixel Linear RGBA:
                                _unsafe.putInt(dstBuffer, addrDst, 0);
                                if (USE_PREV_RES) {
                                    last_out_pixel = 0;
                                }
                            } else {
                                // alpha in range [0; 255]
                                ra = alpha;
                                // color components in range [0; 32385]
                                // no overflow: 15b + 15b < 31b
                                rr = (sr * fs + dr * fd) / alpha;
                                rg = (sg * fs + dg * fd) / alpha;
                                rb = (sb * fs + db * fd) / alpha;

                                // result is Gamma-corrected Linear RGBA.
                                // Inverse Gamma-correction on Linear RGBA: 
                                // color components in range [0; 32385]
                                pixel = (ra << 24)
                                        | (_unsafe.getInt(gam_addr_inv + (rr << 2))) << 16
                                        | (_unsafe.getInt(gam_addr_inv + (rg << 2))) << 8
                                        | (_unsafe.getInt(gam_addr_inv + (rb << 2)));

                                // Linear RGBA components are not pre-multiplied by alpha:
                                // NOP
                                _unsafe.putInt(dstBuffer, addrDst, pixel);
                                if (USE_PREV_RES) {
                                    last_out_pixel = pixel;
                                }
                            }
                        }
                    }
                }
                addrDst += 4L;
            } // for
            addrDst += dstSkip;
            addrMask += maskSkip;
        }
    }
}
