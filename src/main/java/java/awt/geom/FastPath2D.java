/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
package java.awt.geom;

import java.util.Arrays;

/**
 * Custom Path2D.Float class to perform efficient clone (copy only used array parts)
 * and avoid allocating larger arrays
 * or path iteration with array growing:
 * @see java.awt.geom.Path2D.Float(java.awt.Shape,java.awt.geom.AffineTransform)
 */
public final class FastPath2D extends Path2D.Float {

    private static final long serialVersionUID = 1L;

    // only applicable if sun-java2d patch is used: 
    // TODO: use introspection to detect patch in classpath ?
    private final static boolean USE_PATH2D_PATCH = false; 

    public FastPath2D(int initialCapacity) {
        super(WIND_NON_ZERO, initialCapacity);
    }

    FastPath2D(int windingRule,
            byte[] pointTypes,
            int numTypes,
            float[] pointCoords,
            int numCoords) {
        // TODO: provide a Path2D constructor which leaves pointTypes & floatCoords arrays null when given

        // Use initialCapacity=0 to create new byte[0] and new float[0] 
        // as these arrays will be overwritten:
        super(windingRule, 0);
        this.windingRule = windingRule;
        this.pointTypes = pointTypes;
        this.numTypes = numTypes;
        this.floatCoords = pointCoords;
        this.numCoords = numCoords;
    }

    public Path2D trimmedCopy() {
//        System.out.println("copy: numTypes = " + numTypes + ", numCoords = " + numCoords);
        if (USE_PATH2D_PATCH) {
            return new Path2D.Float(this, null);
        }

        // Only copy used array parts:
        return new FastPath2D(this.windingRule,
                Arrays.copyOf(this.pointTypes, this.numTypes),
                this.numTypes,
                Arrays.copyOf(this.floatCoords, this.numCoords),
                this.numCoords);

    }
}
