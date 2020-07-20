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
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;
import static org.marlin.pipe.BlendComposite.NORM_BYTE;
import static org.marlin.pipe.BlendComposite.NORM_BYTE7;
import sun.awt.image.IntegerInterleavedRaster;
import sun.misc.Unsafe;

final class BlendingContextIntSRGB extends BlendComposite.BlendingContext {

    BlendingContextIntSRGB() {
        super();
    }

    @Override
    void compose(final int srcRGBA, final Raster srcIn,
                 final WritableRaster dstOut,
                 final int x, final int y, final int w, final int h,
                 final byte[] mask, final int maskOff, final int maskScan) {

        /*
             System.out.println("srcIn = " + srcIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
         */
        final int extraAlpha = this._extraAlpha; // 7 bits

        final int[] srcBuffer = (srcIn != null) ? ((DataBufferInt) srcIn.getDataBuffer()).getData() : null;
        final int[] dstBuffer = ((DataBufferInt) dstOut.getDataBuffer()).getData();

        final int srcScan = (srcIn != null) ? ((IntegerInterleavedRaster) srcIn).getScanlineStride() : 0;
        // use long to make later math ops in 64b.
        final long dstScan = ((IntegerInterleavedRaster) dstOut).getScanlineStride();

//            final BlendComposite.Blender blender = _blender;
        int pixel;
        int csr, csg, csb, csa;
        int sr, sg, sb, sa;
        int dr, dg, db, da;
        int rr, rg, rb, ra;

        int am, alpha, fs, fd;

        int offSrc = 0;

        final Unsafe _unsafe = OffHeapArray.UNSAFE;

        long addrDst = OffHeapArray.OFF_INT + ((y * dstScan + x) << 2L); // ints
        final long dstSkip = (dstScan - w) << 2L; // ints

        if (mask == null) {
            am = NORM_BYTE;

            // Apply extra alpha:
            // alpha in range [0; 32385] (15bits)
            am *= extraAlpha;

            // int offDst;
            if (am == NORM_ALPHA) {
                // mask with full opacity
                // output = source OVER (totally)
                if (srcIn == null) {
                    for (int j = 0; j < h; j++) {
//                        offDst = (y + j) * dstScan + x;

                        for (int i = 0; i < w; i++, addrDst += 4L) {
                            // pixels are stored as INT_ARGB
                            // our arrays are [R, G, B, A]
                            // Source pixel sRGBA:
                            _unsafe.putInt(dstBuffer, addrDst, srcRGBA);
                        }
                        addrDst += dstSkip;
                    }
                    return;
                }

                for (int j = 0; j < h; j++) {
                    offSrc = (y + j) * srcScan + x;
//                    offDst = (y + j) * dstScan + x;

                    for (int i = 0; i < w; i++, addrDst += 4L) {
                        // pixels are stored as INT_ARGB
                        // our arrays are [R, G, B, A]
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, srcBuffer[offSrc + i]);
                    }
                    addrDst += dstSkip;
                }
                return;
            }

            // Prepare source pixel if constant in tile:
            if (srcIn == null) {
                // Source pixel sRGBA:
                pixel = srcRGBA;

                // sRGBA components are not pre-multiplied by alpha:
                // NOP
                // color components in range [0; 255]
                csr = (pixel >> 16) & NORM_BYTE;
                csg = (pixel >> 8) & NORM_BYTE;
                csb = (pixel) & NORM_BYTE;
                csa = (pixel >> 24) & NORM_BYTE;

                // cs is sRGBA.
            } else {
                csr = 0;
                csg = 0;
                csb = 0;
                csa = 0;
            }

            for (int j = 0; j < h; j++) {
                if (srcIn != null) {
                    offSrc = (y + j) * srcScan + x;
                }
//                offDst = (y + j) * dstScan + x;
                /*
                // Explicit bound checks
                if (offDst < 0 || offDst + w > dstBuffer.length) {
                    break;
                }
                 */
                for (int i = 0; i < w; i++, addrDst += 4L) {
                    // pixels are stored as INT_ARGB
                    // our arrays are [R, G, B, A]

                    if (srcIn == null) {
                        // Copy prepared source pixel:
                        sr = csr;
                        sg = csg;
                        sb = csb;
                        sa = csa;
                    } else {
                        // Source pixel sRGBA:
                        pixel = srcBuffer[offSrc + i];

                        // sRGBA components are not pre-multiplied by alpha:
                        // NOP
                        // color components in range [0; 255]
                        sr = (pixel >> 16) & NORM_BYTE;
                        sg = (pixel >> 8) & NORM_BYTE;
                        sb = (pixel) & NORM_BYTE;
                        sa = (pixel >> 24) & NORM_BYTE;
                    }
                    // srcPixel is Gamma-corrected sRGBA.

                    // fade operator:
                    // Rs = As x Coverage
                    // alpha in range [0; 32385] (15bits)
                    sa = (sa * am) / NORM_BYTE;

                    // Destination pixel:
                    {
                        // Dest pixel sRGBA:
                        pixel = _unsafe.getInt(dstBuffer, addrDst);

                        // sRGBA components are not pre-multiplied by alpha:
                        // NOP
                        // color components in range [0; 255]
                        dr = (pixel >> 16) & NORM_BYTE;
                        dg = (pixel >> 8) & NORM_BYTE;
                        db = (pixel) & NORM_BYTE;
                        da = (pixel >> 24) & NORM_BYTE;

                        // alpha in range [0; 32385] (15bits)
                        da *= NORM_BYTE7;
                    }
                    // dstPixel is Gamma-corrected sRGBA.

                    // Ported-Duff rules in action:
                    // R = S x fs + D x fd
                    // Src Over Dst rule:
                    // fs = Sa
                    // fd = Da x (1 - Sa)
                    // Factors in range [0; 32385] (15bits)
                    fs = (sa); // alpha
                    fd = (da * (NORM_ALPHA - fs)) / NORM_ALPHA;
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
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, 0);
                        continue;
                    }

                    // color components in range [0; 32385]
                    // no overflow: 15b + 15b < 31b
                    rr = (sr * fs + dr * fd) / alpha;
                    rg = (sg * fs + dg * fd) / alpha;
                    rb = (sb * fs + db * fd) / alpha;
                    // alpha in range [0; 255]
                    ra = alpha / NORM_BYTE7;

                    // System.out.println("result: " + Arrays.toString(result));
                    // result is sRGBA.
                    // color components in range [0; 255]
                    pixel = (ra << 24)
                            | (rr << 16)
                            | (rg << 8)
                            | (rb);

                    // sRGBA components are not pre-multiplied by alpha:
                    // NOP
                    _unsafe.putInt(dstBuffer, addrDst, pixel);
                }
                addrDst += dstSkip;
            }
            return;
        }

        // Prepare source pixel if constant in tile:
        if (srcIn == null) {
            // Source pixel sRGBA:
            pixel = srcRGBA;

            // sRGBA components are not pre-multiplied by alpha:
            // NOP
            // color components in range [0; 255]
            csr = (pixel >> 16) & NORM_BYTE;
            csg = (pixel >> 8) & NORM_BYTE;
            csb = (pixel) & NORM_BYTE;
            csa = (pixel >> 24) & NORM_BYTE;

            // cs is sRGBA.
        } else {
            csr = 0;
            csg = 0;
            csb = 0;
            csa = 0;
        }

        long addrMask = OffHeapArray.OFF_BYTE + maskOff; // bytes
        final long maskSkip = maskScan; // bytes

//        int offDst;
//        int offMask = 0;
        for (int j = 0; j < h; j++) {
            if (srcIn != null) {
                offSrc = (y + j) * srcScan + x;
            }
            /*
            offDst = (y + j) * dstScan + x;

            // Explicit bound checks
            if (offDst < 0 || offDst + w > dstBuffer.length) {
                break;
            }
             */
 /*
            offMask = j * maskScan + maskOff;

            // Explicit bound checks
            if (offMask < 0 || offMask + w > mask.length) {
                break;
            }
             */
            for (int i = 0; i < w; i++, addrDst += 4L) {
                // pixels are stored as INT_ARGB
                // our arrays are [R, G, B, A]

                // coverage is stored directly as byte in maskPixel:
//                am = mask[offMask + i] & NORM_BYTE;
                am = _unsafe.getByte(mask, addrMask + i) & NORM_BYTE;

                /*
                 * coverage = 0 means translucent: 
                 * result = destination (already the case)
                 */
                if (am != 0) {
                    // ELSE: coverage between [1;255]:
                    // Apply extra alpha:
                    // alpha in range [0; 32385] (15bits)
                    am *= extraAlpha; // TODO: out of loop if no tile !

                    if (am == NORM_ALPHA) {
                        // mask with full opacity
                        // output = source OVER (totally)
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, (srcIn != null) ? srcBuffer[offSrc + i] : srcRGBA);
                        continue;
                    }

                    if (srcIn == null) {
                        // Copy prepared source pixel:
                        sr = csr;
                        sg = csg;
                        sb = csb;
                        sa = csa;
                    } else {
                        // Source pixel sRGBA:
                        pixel = srcBuffer[offSrc + i];

                        // sRGBA components are not pre-multiplied by alpha:
                        // NOP
                        // color components in range [0; 255]
                        sr = (pixel >> 16) & NORM_BYTE;
                        sg = (pixel >> 8) & NORM_BYTE;
                        sb = (pixel) & NORM_BYTE;
                        sa = (pixel >> 24) & NORM_BYTE;
                    }
                    // srcPixel is Gamma-corrected sRGBA.

                    // fade operator:
                    // Rs = As x Coverage
                    // alpha in range [0; 32385] (15bits)
                    sa = (sa * am) / NORM_BYTE;

                    // Destination pixel:
                    {
                        // Dest pixel sRGBA:
                        pixel = _unsafe.getInt(dstBuffer, addrDst);

                        // sRGBA components are not pre-multiplied by alpha:
                        // NOP
                        // color components in range [0; 255]
                        dr = (pixel >> 16) & NORM_BYTE;
                        dg = (pixel >> 8) & NORM_BYTE;
                        db = (pixel) & NORM_BYTE;
                        da = (pixel >> 24) & NORM_BYTE;

                        // alpha in range [0; 32385] (15bits)
                        da *= NORM_BYTE7;
                    }
                    // dstPixel is Gamma-corrected sRGBA.

                    // Ported-Duff rules in action:
                    // R = S x fs + D x fd
                    // Src Over Dst rule:
                    // fs = Sa
                    // fd = Da x (1 - Sa)
                    // Factors in range [0; 32385] (15bits)
                    fs = (sa); // alpha * 1.0
                    fd = (da * (NORM_ALPHA - fs)) / NORM_ALPHA;
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
                        // Source pixel sRGBA:
                        _unsafe.putInt(dstBuffer, addrDst, 0);
                        continue;
                    }

                    // color components in range [0; 32385]
                    // no overflow: 15b + 15b < 31b
                    rr = (sr * fs + dr * fd) / alpha;
                    rg = (sg * fs + dg * fd) / alpha;
                    rb = (sb * fs + db * fd) / alpha;
                    // alpha in range [0; 255]
                    ra = alpha / NORM_BYTE7;

                    // System.out.println("result: " + Arrays.toString(result));
                    // result is sRGBA.
                    // color components in range [0; 255]
                    pixel = (ra << 24)
                            | (rr << 16)
                            | (rg << 8)
                            | (rb);

                    // sRGBA components are not pre-multiplied by alpha:
                    // NOP
                    _unsafe.putInt(dstBuffer, addrDst, pixel);
                }
            }
            addrDst += dstSkip;
            addrMask += maskSkip;
        }
    }
}
