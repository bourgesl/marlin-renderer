/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.junit.Test;
import sun.java2d.marlin.BoundsTest;
import sun.java2d.marlin.OpenJDKFillBug;
import sun.java2d.marlin.RenderingTest;
import sun.java2d.marlin.TextTransformTest;

/**
 * Simple wrapper on Marlin tests
 */
public class RunJUnitTest {

    private final static String[] NO_ARGS = new String[0];

    @Test
    public void fillBugTest() {
        OpenJDKFillBug.main(NO_ARGS);
    }

    @Test
    public void boundsTest() {
        BoundsTest.main(NO_ARGS);
    }

    @Test
    public void renderingTest() {
        RenderingTest.main(NO_ARGS);
    }

    @Test
    public void arrayCacheSizeTest() {
        ArrayCacheSizeTest.main(NO_ARGS);
    }

    @Test
    public void textClipErrorTest() {
        TextClipErrorTest.main(NO_ARGS);
    }

    @Test
    public void textTransformTest() {
        TextTransformTest.main(NO_ARGS);
    }

    @Test
    public void crashTest() {
        CrashTest.main(NO_ARGS);
    }

    @Test
    public void crashPaintTest() {
        CrashPaintTest.main(NO_ARGS);
    }

    @Test
    public void crashNaNTest() {
        CrashNaNTest.main(NO_ARGS);
    }

    @Test
    public void blockFlagTest() {
        BlockFlagTest.main(NO_ARGS);
    }

    @Test
    public void CeilAndFloorTest() {
        CeilAndFloorTests.main(NO_ARGS);
    }

    @Test
    public void path2DGrowTest() {
        Path2DGrow.main(NO_ARGS);
    }

    @Test
    public void path2DCopyConstructorTest() {
        Path2DCopyConstructor.main(NO_ARGS);
    }

    @Test
    public void imageWriterCompressionTest() {
        ImageWriterCompressionTest.main(NO_ARGS);
    }
}
