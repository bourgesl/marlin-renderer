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

import java.awt.AlphaComposite;
import org.marlin.pisces.MarlinProperties;
import sun.java2d.SunGraphics2D;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.AlphaColorPipe;

public final class MarlinCompositor {

    public final static boolean ENABLE_COMPOSITOR = "true".equals(System.getProperty("sun.java2d.renderer.compositor", "true"))
            && isJava2dPipelinePatched();

    public final static boolean ENABLE_FILL = ENABLE_COMPOSITOR
            && "true".equals(System.getProperty("sun.java2d.renderer.compositor.fill", "true"));

    public final static double GAMMA_sRGB = 2.4;
    public final static double GAMMA_LUMA = 2.2; // home made (4K panel)
    public final static double GAMMA_Y_to_L = 3.0; // TODO: KILL

    /* 2.4 is the standard sRGB gamma */
    public final static double GAMMA = MarlinProperties.getDouble("sun.java2d.renderer.gamma", GAMMA_sRGB, 0.1, 3.0);

    public final static String BLEND_FIX = System.getProperty("sun.java2d.renderer.compositor.fix", "contrast"); // 'contrast' or 'lum'

    public final static boolean FIX_LUM = BLEND_FIX.equals("lum");
    public final static boolean FIX_CONTRAST = BLEND_FIX.equals("contrast");

    /* 3.0 is the standard L*(Y) gamma */
    public final static double LUMA_GAMMA = MarlinProperties.getDouble("sun.java2d.renderer.blend.gamma", GAMMA_LUMA, 0.1, 3.0);

    public final static boolean BLEND_QUALITY = "true".equals(System.getProperty("sun.java2d.renderer.compositor.quality", "false"));

    /* contrast adjustement (0..1) */
    public final static double BLEND_CONTRAST = MarlinProperties.getDouble("sun.java2d.renderer.contrast", 1.0, 0.0, 2.0);

    public final static boolean USE_OLD_BLENDER = (GAMMA == 1.0);

    static {
        System.out.println("INFO: Marlin Compositor (Java implementation of correct alpha compositing)");
        System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.compositor = " + ENABLE_COMPOSITOR);

        if (ENABLE_COMPOSITOR) {
            System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.compositor.fill = " + ENABLE_FILL);
            System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.gamma = " + GAMMA);
            System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.compositor.fix = " + BLEND_FIX);
            System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.compositor.quality = " + BLEND_QUALITY);
            System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.blend.gamma = " + LUMA_GAMMA);
            if (FIX_CONTRAST && !USE_OLD_BLENDER) {
                System.out.println("INFO: Marlin Compositor: sun.java2d.renderer.contrast = " + BLEND_CONTRAST);
            }
            System.out.println("INFO: Marlin Compositor: Gamma correction only supports following surface types [IntArgb]."); // , FourByteAbgr
            System.out.println("INFO: Marlin Compositor: Premultiplied formats [IntArgbPre] are also working with lower visual quality."); // , FourByteAbgrPre
        }
    }

    private MarlinCompositor() {
        // factory
    }

    private final static boolean isJava2dPipelinePatched() {
        boolean present = false;
        try {
            present = AlphaColorPipe.isMarlinPatched();
        } catch (Error e) {
            System.out.println("Marlin sun-java2d.jar patch is not loaded.");
            // e.printStackTrace();
        }
        return present;
    }

    public final static boolean isSupported(final SunGraphics2D sg) {

        if (sg.composite instanceof AlphaComposite) {
            final AlphaComposite ac = (AlphaComposite) sg.composite;

            if (ac.getRule() != AlphaComposite.SRC_OVER) {
                // only SrcOver implemented for now
                return false;
            }
            // TODO: implement all Porter-Duff rules 
        }
        return isSurfaceSupported(sg.getSurfaceData().getSurfaceType());
    }

    public final static boolean isSurfaceSupported(final SurfaceType sdt) {
        // check supported types (basic surfaces, not volatile or accelerated surfaces):
        if ((sdt != SurfaceType.IntArgb) && (sdt != SurfaceType.IntArgbPre) // TODO: fix 4bytes belnding ...
                /*  && (sdt != SurfaceType.FourByteAbgr) && (sdt != SurfaceType.FourByteAbgrPre) */) {
            System.out.println("Unsupported surface type: " + sdt);
            return false; // means invalid pipe
        }
        // System.out.println("Supported surface type: " + sdt);
        return true;
    }

    // Per-thread CompositorSettings (very small so do not use any Soft or Weak Reference)
    private static final ThreadLocal<CompositorSettings> settingsThreadLocal = new ThreadLocal<CompositorSettings>() {
        @Override
        protected CompositorSettings initialValue() {
            return new CompositorSettings();
        }
    };

    public final static CompositorSettings getCompositorSettings() {
        return settingsThreadLocal.get();
    }

    public final static boolean isGammaCorrectionEnabled() {
        return getCompositorSettings().isGammaCorrectionEnabled();
    }
}
