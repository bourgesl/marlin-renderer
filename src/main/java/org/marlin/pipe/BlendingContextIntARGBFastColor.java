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

import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import static org.marlin.pipe.BlendComposite.luminance10b;
import sun.awt.image.IntegerInterleavedRaster;
import sun.java2d.SunGraphics2D;
import static sun.java2d.SunGraphics2D.PAINT_ALPHACOLOR;
import sun.misc.Unsafe;

final class BlendingContextIntARGBFastColor extends BlendComposite.BlendingContext {

    private final static boolean DO_DIVIDE = false;
    private final static boolean DO_ROUND = false;

    BlendingContextIntARGBFastColor() {
        super();
    }

    // members
    private final long _gam_addr_dir = BlendComposite.GAMMA_LUT.LUT_UNSAFE_dir.start;
    private final long _gam_addr_inv = BlendComposite.GAMMA_LUT.LUT_UNSAFE_inv.start;
    private final long _lt_addr = AlphaLUT.ALPHA_LUT.LUMA_TABLE_UNSAFE.start;
    private final long _ai_addr = AlphaLUT.ALPHA_LUT.ALPHA_INDEX_UNSAFE.start;
    private final long _at_addr = AlphaLUT.ALPHA_LUT.ALPHA_TABLES_UNSAFE.start;
    // initialize values for (0,0,0)
    private int _last_src_pixel = 0;
    private int _sa = 0, _sr = 0, _sg = 0, _sb = 0;
    private long _ai_addr_ls = _ai_addr; // for ls=0 (default)

    @Override
    BlendComposite.BlendingContext init(final BlendComposite composite, final SunGraphics2D sg) {
        // this._blender = BlendComposite.Blender.getBlenderFor(composite);
        // this._extraAlpha = 0; // unsupported

        // Prepare source pixel if constant in tile:
        if (sg.paintState <= PAINT_ALPHACOLOR) {
            // use Sungraphics2D.eargb = ie extra alpha is pre-blended into SRC ALPHA:
            // Source pixel sRGBA:
            int pixel = sg.eargb;

            if (pixel != _last_src_pixel) {
                _last_src_pixel = pixel;

                final long gam_addr_dir = _gam_addr_dir;

                final int NORM_BYTE = BlendComposite.NORM_BYTE;

                final Unsafe _unsafe = OffHeapArray.UNSAFE;

                // Linear RGBA components are not pre-multiplied by alpha:
                // NOP
                // Gamma-correction to Linear RGBA: 
                // color components in range [0; 32385]
                _sa = (pixel >> 24) & NORM_BYTE; // [0; 255]

                _sr = _unsafe.getInt(gam_addr_dir + (((pixel >> 16) & NORM_BYTE) << 2));
                _sg = _unsafe.getInt(gam_addr_dir + (((pixel >> 8) & NORM_BYTE) << 2));
                _sb = _unsafe.getInt(gam_addr_dir + (((pixel) & NORM_BYTE) << 2));

                // src Pixel is Gamma-corrected Linear RGBA.
                final int ls = luminance10b(_sr, _sg, _sb); // Y (10bits)
                _ai_addr_ls = _ai_addr;
                if (ls != 0) {
                    // lookup offset to table:
                    _ai_addr_ls += (_unsafe.getInt(_lt_addr + (ls << 2)) << 7);
                }
            }
        }
        return this; // fluent API
    }

    @Override
    void compose(final int srcRGBA, final Raster srcIn,
                 final WritableRaster dstOut,
                 final int x, final int y, final int w, int h,
                 final byte[] mask, final int maskOff, final int maskScan) {

        // Ignore srcIn (color only)
        /*
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        // Ensure srcRGBA == _last_src_pixel
        // srcRGBA = _last_src_pixel;
        final int[] dstBuffer = ((DataBufferInt) dstOut.getDataBuffer()).getData();

        // use long to make later math ops in 64b.
        final long dstScan = ((IntegerInterleavedRaster) dstOut).getScanlineStride();

//            final BlendComposite.Blender blender = _blender;
        final long gam_addr_dir = _gam_addr_dir;
        final long gam_addr_inv = _gam_addr_inv;

        final int NORM_BYTE = BlendComposite.NORM_BYTE;
        // less round precision but enough for now !
        final int NORM_BYTE7 = BlendComposite.NORM_BYTE7;

        long ai_addr_ls, at_addr_a;
        int pixel, last_out_pixel = 0, last_dst_pixel = 0;
        int sa, sr, sg, sb;
        int da = 0, dr = 0, dg = 0, db = 0;
        int am, alpha, last_alpha = 0, fs, fd;
        int rr, rg, rb;
        int ld;

        final Unsafe _unsafe = OffHeapArray.UNSAFE;
        long addrDst = OffHeapArray.OFF_INT + ((y * dstScan + x) << 2); // ints

        final long at_addr = _at_addr;

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
                if (USE_LONG && (w >= 2)) {
                    final int w2 = (w >> 1);
                    final long l2_srcRGBA = (((long) srcRGBA) << 32L) + (srcRGBA & 0xFFFFFFFFL);

                    if ((w & 1) == 1) {
                        final long dstSkip = (dstScan - w + 1) << 2L; // ints

                        for (; --h >= 0;) {
                            for (int i = w2; --i >= 0;) {
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

                    for (; --h >= 0;) {
                        for (int i = w2; --i >= 0;) {
                            _unsafe.putLong(dstBuffer, addrDst, l2_srcRGBA);
                            addrDst += 8L;
                        }
                        addrDst += dstSkip;
                    }
                    return;
                }
                // else:
                final long dstSkip = (dstScan - w) << 2L; // ints

                for (; --h >= 0;) {
                    for (int i = w; --i >= 0;) {
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
            ai_addr_ls = _ai_addr_ls; // for ld=0 (default)
            at_addr_a = at_addr;
        }

        // cases done: (sa == 0) and (sa == FF & mask null = FF)
        final long dstSkip = (dstScan - w) << 2L; // ints

        long addrMask = OffHeapArray.OFF_BYTE + maskOff; // bytes
        final long maskSkip = maskScan - w; // bytes

        final long lt_addr = _lt_addr;

        for (; --h >= 0;) {
            for (int i = w; --i >= 0;) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
                am = (mask != null) ? (_unsafe.getByte(mask, addrMask++) & NORM_BYTE) : NORM_BYTE;

                // coverage = 0 means translucent:
                // result = destination (already the case)
                if (am != 0) {
                    // ELSE: coverage between [1;255]:
                    // srcPixel is Gamma-corrected Linear RGBA.
                    if ((am == NORM_BYTE) && (sa == NORM_BYTE)) {
                        // mask with full opacity
                        // output = source OVER (totally)
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, srcRGBA);
                    } else {
                        // case done: (sa == FF & mask = FF)

                        boolean prev = true; // same src pixel

                        // Destination pixel:
                        // Dest pixel Linear RGBA:
                        pixel = _unsafe.getInt(dstBuffer, addrDst);

                        if (pixel != last_dst_pixel) {
                            last_dst_pixel = pixel;
                            prev = false;

                            // Linear RGBA components are not pre-multiplied by alpha:
                            // NOP
                            // Gamma-correction to Linear RGBA: 
                            // color components in range [0; 32385]
                            da = (pixel >> 24) & NORM_BYTE; // [0; 255]

                            dr = _unsafe.getInt(gam_addr_dir + (((pixel >> 16) & NORM_BYTE) << 2));
                            dg = _unsafe.getInt(gam_addr_dir + (((pixel >> 8) & NORM_BYTE) << 2));
                            db = _unsafe.getInt(gam_addr_dir + (((pixel) & NORM_BYTE) << 2));

                            // in range [0; 32385] (15bits):
                            ld = luminance10b(dr, dg, db); // Y (10bits)
                            // TODO: use da to weight luminance ld ?
                            at_addr_a = at_addr;
                            if (ld != 0) {
                                // lookup offset to table:
                                at_addr_a += _unsafe.getInt(ai_addr_ls
                                        + (_unsafe.getInt(lt_addr + (ld << 2)) << 2));
                            }
                        }
                        // dstPixel is Gamma-corrected Linear RGBA.

                        // fade operator:
                        // Rs = As x Coverage
                        // ALPHA in range [0; 255]
                        if (sa == NORM_BYTE) {
                            // most probable: SRC is OPAQUE
                            alpha = am;
                        } else if (am == NORM_BYTE) {
                            // full opacity: fills or plain coverage
                            alpha = sa;
                        } else {
                            if (DO_DIVIDE) {
                                alpha = (sa * am + NORM_BYTE7) / NORM_BYTE;
                            } else {
                                if (DO_ROUND) {
                                    alpha = sa * am + NORM_BYTE7; // round
                                } else {
                                    alpha = sa * am; // no-round
                                }
                                // DivideBy255(int value): return (value + 1 + (value >> 8)) >> 8;
                                // or (((x) + (((x) + 257) >> 8)) >> 8)
                                alpha = (alpha + ((alpha + 257) >> 8)) >> 8; // div 255
                            }
                        }

                        if (alpha != last_alpha) {
                            last_alpha = alpha;
                            prev = false;
                        }

                        // same src, dst pixels and alpha:
                        if (!prev) {
                            // Adjust ALPHA (linear in L*):
                            alpha = _unsafe.getInt(at_addr_a + (alpha << 2)); // ALPHA in range [0; 255]

                            // check again extrema: 0 or 255 after correction:
                            if (alpha == 0) {
                                // coverage = 0 means translucent:
                                // result = destination (already the case)
                                last_out_pixel = last_dst_pixel;
                            } else {
                                if ((alpha == NORM_BYTE) && (sa == NORM_BYTE)) {
                                    // mask with full opacity
                                    // output = source OVER (totally)
                                    last_out_pixel = srcRGBA;
                                } else {
                                    // Ported-Duff rules in action:
                                    // R = S x fs + D x fd
                                    // Src Over Dst rule:
                                    // fs = Sa
                                    // fd = Da x (1 - Sa)
                                    // Factors in range [0; 255]
                                    fs = (alpha); // alpha

                                    // fix da -> alpha:
                                    fd = (NORM_BYTE - alpha);

                                    // Most probable: DST is OPAQUE
                                    if ((da != NORM_BYTE) && (fd != 0)) {
                                        if (DO_DIVIDE) {
                                            fd = (da * fd + NORM_BYTE7) / NORM_BYTE; // TODO: avoid divide and use [0; 255]
                                        } else {
                                            if (DO_ROUND) {
                                                fd = da * fd + NORM_BYTE7; // round
                                            } else {
                                                fd = da * fd; // no-round
                                            }
                                            fd = (fd + ((fd + 257) >> 8)) >> 8; // div 255
                                        }
                                    }

                                    // blender.blend(srcPixel, dstPixel, fs, fd, result);
                                    // ALPHA in range [0; 255]
                                    alpha = fs + fd;

                                    // Impossible case : always false by previous checks
                                    if (alpha == 0) {
                                        // output = none
                                        last_out_pixel = 0;
                                    } else {
                                        // alpha in range [0; 255]
                                        // color components in range [0; 32385]
                                        // no overflow: 15b + 8b
                                        if (DO_ROUND) {
                                            rr = (sr * fs + dr * fd) + NORM_BYTE7; // round
                                            rg = (sg * fs + dg * fd) + NORM_BYTE7; // round
                                            rb = (sb * fs + db * fd) + NORM_BYTE7; // round
                                        } else {
                                            rr = (sr * fs + dr * fd);
                                            rg = (sg * fs + dg * fd);
                                            rb = (sb * fs + db * fd);
                                        }

                                        // Most probable: OUT is OPAQUE (255 div)
                                        if ((alpha != NORM_BYTE) || DO_DIVIDE) {
                                            rr /= alpha;
                                            rg /= alpha;
                                            rb /= alpha;
                                        } else {
                                            rr = (rr + ((rr + 257) >> 8)) >> 8; // div 255
                                            rg = (rg + ((rg + 257) >> 8)) >> 8; // div 255
                                            rb = (rb + ((rb + 257) >> 8)) >> 8; // div 255
                                        }

                                        // result is Gamma-corrected Linear RGBA.
                                        // Inverse Gamma-correction to Linear RGBA: 
                                        // color components in range [0; 32385]
                                        last_out_pixel = (alpha << 24)
                                                | (_unsafe.getInt(gam_addr_inv + (rr << 2))) << 16
                                                | (_unsafe.getInt(gam_addr_inv + (rg << 2))) << 8
                                                | (_unsafe.getInt(gam_addr_inv + (rb << 2)));
                                    }
                                }
                            }
                        }
                        // set output anyway:
                        // low probability (boundaries) to have (last_out_pixel == last_dst_pixel)

                        // Linear RGBA components are not pre-multiplied by alpha:
                        // NOP
                        _unsafe.putInt(dstBuffer, addrDst, last_out_pixel);
                    }
                }
                addrDst += 4L;
            } // for
            addrDst += dstSkip;
            addrMask += maskSkip;
        }
    }
}
