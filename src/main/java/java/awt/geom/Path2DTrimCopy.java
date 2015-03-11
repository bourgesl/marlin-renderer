/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Using the java.awt.geom package is not allowed by jtreg but there is no
 * practical mean to access Path2D fields (arrays) to check them. 
 * Run this test manually
 * 
 * @summary Check Path2D copy constructor modified to trim arrays
 * @run main Path2DTrimCopy
 */
public class Path2DTrimCopy {

    private static final int LEN = 87;

    private static final int GROWTH_LEN = 128;

    public static void main(String[] args) {
        testPath2D_Float();
        testPath2D_Float_empty();

        testPath2D_Double();
        testPath2D_Double_empty();
    }

    public static void testPath2D_Float() {
        System.out.println("testPath2D_Float()");
        Path2D.Float cf;

        // Fill new path:
        final Path2D.Float pf = new Path2D.Float();
        fillPath(pf, LEN);

        System.out.println("numTypes: " + pf.numTypes);
        System.out.println("pointTypes.length: " + pf.pointTypes.length);
        System.out.println("numCoords: " + pf.numCoords);
        System.out.println("floatCoords.length: " + pf.floatCoords.length);

        // Test cloned path:
        cf = new Path2D.Float(pf);
        System.out.println("cloned pointTypes.length: "
                + cf.pointTypes.length);
        System.out.println("cloned floatCoords.length: "
                + cf.floatCoords.length);

        if (cf.numTypes != pf.numTypes) {
            fail("Invalid cloned numTypes: "
                    + cf.numTypes + " != " + pf.numTypes);
        }
        if (cf.pointTypes.length != cf.numTypes) {
            fail("Invalid cloned pointTypes: "
                    + cf.pointTypes.length + " != " + cf.numTypes);
        }
        if (cf.numCoords != pf.numCoords) {
            fail("Invalid cloned numCoords: "
                    + cf.numCoords + " != " + pf.numCoords);
        }
        if (cf.floatCoords.length != cf.numCoords) {
            fail("Invalid cloned floatCoords: "
                    + cf.floatCoords.length + " != " + cf.numCoords);
        }
    }

    public static void testPath2D_Float_empty() {
        System.out.println("testPath2D_Float_empty()");
        Path2D.Float cf;

        {
            // Create empty path:
            Path2D.Float pf = new Path2D.Float();

            System.out.println("numTypes: " + pf.numTypes);
            System.out.println("pointTypes.length: " + pf.pointTypes.length);
            System.out.println("numCoords: " + pf.numCoords);
            System.out.println("floatCoords.length: " + pf.floatCoords.length);

            // Test cloned path:
            cf = new Path2D.Float(pf);
            System.out.println("cloned pointTypes.length: "
                    + cf.pointTypes.length);
            System.out.println("cloned floatCoords.length: "
                    + cf.floatCoords.length);

            if (cf.numTypes != pf.numTypes) {
                fail("Invalid cloned numTypes: "
                        + cf.numTypes + " != " + pf.numTypes);
            }
            if (cf.pointTypes.length != cf.numTypes) {
                fail("Invalid cloned pointTypes: "
                        + cf.pointTypes.length + " != " + cf.numTypes);
            }
            if (cf.numCoords != pf.numCoords) {
                fail("Invalid cloned numCoords: "
                        + cf.numCoords + " != " + pf.numCoords);
            }
            if (cf.floatCoords.length != cf.numCoords) {
                fail("Invalid cloned floatCoords: "
                        + cf.floatCoords.length + " != " + cf.numCoords);
            }
        }

        // Fill path:
        fillPath(cf, LEN);

        System.out.println("numTypes: " + cf.numTypes);
        System.out.println("pointTypes.length: " + cf.pointTypes.length);
        System.out.println("numCoords: " + cf.numCoords);
        System.out.println("floatCoords.length: " + cf.floatCoords.length);

        final int n = LEN + 1;

        if (cf.numTypes != n) {
            fail("Invalid cloned numTypes: "
                    + cf.numTypes + " != " + n);
        }
        if (cf.pointTypes.length != GROWTH_LEN) {
            fail("Invalid cloned pointTypes: "
                    + cf.pointTypes.length + " != " + GROWTH_LEN);
        }
        if (cf.numCoords != n * 2) {
            fail("Invalid cloned numCoords: "
                    + cf.numCoords + " != " + (n * 2));
        }
        if (cf.floatCoords.length != 2 * GROWTH_LEN) {
            fail("Invalid cloned floatCoords: "
                    + cf.floatCoords.length + " != " + (2 * GROWTH_LEN));
        }
    }

    public static void testPath2D_Double() {
        System.out.println("testPath2D_Double()");
        Path2D.Double cd;

        // Fill new path:
        final Path2D.Double pd = new Path2D.Double();
        fillPath(pd, LEN);

        System.out.println("numTypes: " + pd.numTypes);
        System.out.println("pointTypes.length: " + pd.pointTypes.length);
        System.out.println("numCoords: " + pd.numCoords);
        System.out.println("doubleCoords.length: " + pd.doubleCoords.length);

        // Test cloned path:
        cd = new Path2D.Double(pd);
        System.out.println("cloned pointTypes.length: "
                + cd.pointTypes.length);
        System.out.println("cloned doubleCoords.length: "
                + cd.doubleCoords.length);

        if (cd.numTypes != pd.numTypes) {
            fail("Invalid cloned numTypes: "
                    + cd.numTypes + " != " + pd.numTypes);
        }
        if (cd.pointTypes.length != cd.numTypes) {
            fail("Invalid cloned pointTypes: "
                    + cd.pointTypes.length + " != " + cd.numTypes);
        }
        if (cd.numCoords != pd.numCoords) {
            fail("Invalid cloned numCoords: "
                    + cd.numCoords + " != " + pd.numCoords);
        }
        if (cd.doubleCoords.length != cd.numCoords) {
            fail("Invalid cloned doubleCoords: "
                    + cd.doubleCoords.length + " != " + cd.numCoords);
        }
    }

    public static void testPath2D_Double_empty() {
        System.out.println("testPath2D_Double_empty()");

        Path2D.Double cf;

        {
            // Create empty path:
            Path2D.Double pf = new Path2D.Double();

            System.out.println("numTypes: " + pf.numTypes);
            System.out.println("pointTypes.length: " + pf.pointTypes.length);
            System.out.println("numCoords: " + pf.numCoords);
            System.out.println("floatCoords.length: " + pf.doubleCoords.length);

            // Test cloned path:
            cf = new Path2D.Double(pf);
            System.out.println("cloned pointTypes.length: "
                    + cf.pointTypes.length);
            System.out.println("cloned doubleCoords.length: "
                    + cf.doubleCoords.length);

            if (cf.numTypes != pf.numTypes) {
                fail("Invalid cloned numTypes: "
                        + cf.numTypes + " != " + pf.numTypes);
            }
            if (cf.pointTypes.length != cf.numTypes) {
                fail("Invalid cloned pointTypes: "
                        + cf.pointTypes.length + " != " + cf.numTypes);
            }
            if (cf.numCoords != pf.numCoords) {
                fail("Invalid cloned numCoords: "
                        + cf.numCoords + " != " + pf.numCoords);
            }
            if (cf.doubleCoords.length != cf.numCoords) {
                fail("Invalid cloned doubleCoords: "
                        + cf.doubleCoords.length + " != " + cf.numCoords);
            }
        }

        // Fill path:
        fillPath(cf, LEN);

        System.out.println("numTypes: " + cf.numTypes);
        System.out.println("pointTypes.length: " + cf.pointTypes.length);
        System.out.println("numCoords: " + cf.numCoords);
        System.out.println("doubleCoords.length: " + cf.doubleCoords.length);

        final int n = LEN + 1;

        if (cf.numTypes != n) {
            fail("Invalid cloned numTypes: "
                    + cf.numTypes + " != " + n);
        }
        if (cf.pointTypes.length != GROWTH_LEN) {
            fail("Invalid cloned pointTypes: "
                    + cf.pointTypes.length + " != " + GROWTH_LEN);
        }
        if (cf.numCoords != n * 2) {
            fail("Invalid cloned numCoords: "
                    + cf.numCoords + " != " + (n * 2));
        }
        if (cf.doubleCoords.length != 2 * GROWTH_LEN) {
            fail("Invalid cloned doubleCoords: "
                    + cf.doubleCoords.length + " != " + (2 * GROWTH_LEN));
        }
    }

    private static void fillPath(Path2D p, int len) {
        p.moveTo(0, 0);
        for (int i = 0; i < len; i++) {
            p.lineTo(i, i);
        }
    }

    private static void fail(String msg) {
        System.out.println("Test Failed");
        throw new RuntimeException(msg);
    }
}
