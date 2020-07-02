/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
package sun.java2d.pipe;

import java.awt.Rectangle;
import java.awt.Shape;
import org.marlin.pipe.CompositorSettings;
import org.marlin.pipe.GammaCompositePipe;
import org.marlin.pipe.MarlinCompositor;
import sun.java2d.SunGraphics2D;


/**
 * This class implements a CompositePipe that renders path alpha tiles
 * into a destination that supports direct alpha compositing of a solid
 * color, according to one of the rules in the AlphaComposite class.
 */
public class AlphaColorPipe implements CompositePipe, ParallelogramPipe {

    // Hack to detect this class patch is present
    public final static boolean isMarlinPatched() {
        return true;
    }

    public AlphaColorPipe() {
    }

    @Override
    public Object startSequence(final SunGraphics2D sg, final Shape s, final Rectangle dev, final int[] abox) {
        if (MarlinCompositor.ENABLE_COMPOSITOR && MarlinCompositor.isSupported(sg)) {
            final Object ctx = GammaCompositePipe.INSTANCE.startSequence(MarlinCompositor.getCompositorSettings().getCompositorContext(), sg, s, dev, abox);
            if (ctx != null) {
                // gamma correction is enabled
                return ctx;
            }
        }
        return sg;
    }

    @Override
    public boolean needTile(final Object context, final int x, final int y, final int w, final int h) {
        return true;
    }

    @Override
    public void renderPathTile(final Object context,
                               final byte[] atile, final int offset, final int tilesize,
                               final int x, final int y, final int w, final int h) {

        if (context instanceof SunGraphics2D) {
            // System.out.println("renderPathTile uses MaskFill !");

            final SunGraphics2D sg = (SunGraphics2D) context;

            sg.alphafill.MaskFill(sg, sg.getSurfaceData(), sg.composite,
                    x, y, w, h,
                    atile, offset, tilesize);
            return;
        }

        // GammaCompositePipe in action
        // System.out.println("renderPathTile uses GammaCompositePipe !");
        GammaCompositePipe.INSTANCE.renderPathTile(context, atile, offset, tilesize, x, y, w, h);
    }

    @Override
    public void skipTile(final Object context, final int x, final int y) {
    }

    @Override
    public void endSequence(final Object context) {
        if (!(context instanceof SunGraphics2D)) {
            // GammaCompositePipe in action
            GammaCompositePipe.INSTANCE.endSequence(context);
        }
    }

    @Override
    public void fillParallelogram(SunGraphics2D sg,
                                  double ux1, double uy1,
                                  double ux2, double uy2,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2)
    {
        sg.alphafill.FillAAPgram(sg, sg.getSurfaceData(), sg.composite,
                                 x, y, dx1, dy1, dx2, dy2);
    }

    @Override
    public void drawParallelogram(SunGraphics2D sg,
                                  double ux1, double uy1,
                                  double ux2, double uy2,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2,
                                  double lw1, double lw2)
    {
        sg.alphafill.DrawAAPgram(sg, sg.getSurfaceData(), sg.composite,
                                 x, y, dx1, dy1, dx2, dy2, lw1, lw2);
    }
}
