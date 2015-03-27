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


import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.IllegalPathStateException;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

/**
 * @summary Check Path2D copy constructor modified to trim arrays
 * @run main Path2DTrimCopy
 */
public class Path2DCopyConstructor {

    private final static float EPSILON = 5e-6f;
    private final static float FLATNESS = 1e-2f;

    private final static AffineTransform at 
        = AffineTransform.getScaleInstance(1.3, 2.4);

    private final static Rectangle2D.Double rect2d 
        = new Rectangle2D.Double(3.2, 4.1, 5.0, 10.0);

    private final static Point2D.Double pt2d 
        = new Point2D.Double(2.0, 2.5);

    public static void main(String[] args) {
        testDoublePaths();

        testFloatPaths();
        testGeneralPath();
    }

    static void testDoublePaths() {
        System.out.println("\n - Test(Path2D.Double) ---");
        test(() -> new Path2D.Double());
    }

    static void testFloatPaths() {
        System.out.println("\n - Test(Path2D.Float) ---");
        test(() -> new Path2D.Float());
    }

    static void testGeneralPath() {
        System.out.println("\n - Test(GeneralPath) ---");
        test(() -> new GeneralPath());
    }

    interface PathFactory {

        Path2D makePath();
    }

    /**
     - Perform each of the above tests on a (set of) clone(s) of the following paths:
     - each of the following executed on both a Float and a Double instance...
     - an empty path
     - a path with just a moveto
     - a path with a moveto+some lines
     - a path with a moveto+some curves
     */
    static void test(PathFactory pf) {
        System.out.println("\n --- test: path(empty) ---");
        test(pf.makePath(), true);
        System.out.println("\n\n --- test: path(addMove) ---");
        test(addMove(pf.makePath()), false);
        System.out.println("\n\n --- test: path(addMove + addLines) ---");
        test(addLines(pf.makePath()), false);
        System.out.println("\n\n --- test: path(addMove + addCurves) ---");
        test(addCurves(pf.makePath()), false);
    }

    static Path2D addMove(Path2D p2d) {
        p2d.moveTo(1.0, 0.5);
        return p2d;
    }

    static Path2D addLines(Path2D p2d) {
        addMove(p2d);
        addLinesOnly(p2d);
        return p2d;
    }

    static Path2D addLinesOnly(Path2D p2d) {
        for (int i = 0; i < 10; i++) {
            p2d.lineTo(1.1 * i, 2.3 * i);
        }
        return p2d;
    }

    static Path2D addCurves(Path2D p2d) {
        addMove(p2d);
        addCurvesOnly(p2d);
        return p2d;
    }

    static Path2D addCurvesOnly(Path2D p2d) {
        for (int i = 0; i < 10; i++) {
            p2d.quadTo(1.1 * i, 1.2 * i, 1.3 * i, 1.4 * i);
            p2d.curveTo(1.1 * i, 1.2 * i, 1.3 * i, 1.4 * i, 1.5 * i, 1.6 * i);
        }
        return p2d;
    }

    /**
     - Write tests that create paths in various forms and run them through the following sub-tests:
     - each of the following should be tested on a fresh clone...
     - get a PathIterator and iterate it through until it is done
     (optional - compare to the expected segments that were in the original)
     - get a flattened PathIterator using "getPathIterator(flatness,)" and make sure it works
     (optional - compare to original path if the original was already flat)
     (but, also run it on a path with curves to make sure flattening engine didn't break)
     - add various kinds of segments to the cloned path
     - get the bounds of the cloned path
     - use the transform() method on the cloned path
     - call intersects(point), intersects(rect) and contains(rect) on a cloned path
     - call getCurrentPoint() on the cloned path
     */
    static void test(Path2D p2d, boolean isEmpty) {
        testEqual(new Path2D.Float(p2d), p2d);
        testEqual(new Path2D.Double(p2d), p2d);
        testEqual(new GeneralPath(p2d), p2d);

        // 3 clone variants:
        testIterator(new Path2D.Float(p2d), p2d);
        testIterator(new Path2D.Double(p2d), p2d);
        testIterator((Path2D) p2d.clone(), p2d);

        // 3 clone variants:
        testFlattening(new Path2D.Float(p2d), p2d);
        testFlattening(new Path2D.Double(p2d), p2d);
        testFlattening((Path2D) p2d.clone(), p2d);

        // 3 clone variants:
        testAddMove(new Path2D.Float(p2d));
        testAddMove(new Path2D.Double(p2d));
        testAddMove((Path2D) p2d.clone());

        // These should expect exception if empty
        // 3 clone variants:
        testAddLine(new Path2D.Float(p2d), isEmpty);
        testAddLine(new Path2D.Double(p2d), isEmpty);
        testAddLine((Path2D) p2d.clone(), isEmpty);

        // 3 clone variants:
        testAddQuad(new Path2D.Float(p2d), isEmpty);
        testAddQuad(new Path2D.Double(p2d), isEmpty);
        testAddQuad((Path2D) p2d.clone(), isEmpty);

        // 3 clone variants:
        testGetBounds(new Path2D.Float(p2d), p2d);
        testGetBounds(new Path2D.Double(p2d), p2d);
        testGetBounds((Path2D) p2d.clone(), p2d);

        // 3 clone variants:
        testTransform(new Path2D.Float(p2d));
        testTransform(new Path2D.Double(p2d));
        testTransform((Path2D) p2d.clone());

        // 3 clone variants:
        testIntersect(new Path2D.Float(p2d), p2d);
        testIntersect(new Path2D.Double(p2d), p2d);
        testIntersect((Path2D) p2d.clone(), p2d);

        // 3 clone variants:
        testContains(new Path2D.Float(p2d), p2d);
        testContains(new Path2D.Double(p2d), p2d);
        testContains((Path2D) p2d.clone(), p2d);

        // 3 clone variants:
        testGetCurrentPoint(new Path2D.Float(p2d), p2d);
        testGetCurrentPoint(new Path2D.Double(p2d), p2d);
        testGetCurrentPoint((Path2D) p2d.clone(), p2d);
    }

    static void testEqual(Path2D pathA, Path2D pathB) {
        // Grab 2 path iterators and test for equality with float coords[]
        final PathIterator itA = pathA.getPathIterator(null);
        final PathIterator itB = pathB.getPathIterator(null);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + " type are not equals [" + typeA + "|" + typeB + "] !");
            }
            if (!Arrays.equals(coordsA, coordsB)) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|" 
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        System.out.println("testEqual: " + n + " segments.");
    }

    static void testIterator(Path2D pathA, Path2D pathB) {
        // - get a PathIterator and iterate it through until it is done
        // (optional - compare to the expected segments that were in the original)
        final PathIterator itA = pathA.getPathIterator(at);
        final PathIterator itB = pathB.getPathIterator(at);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + "type are not equals [" + typeA + "|" + typeB + "] !");
            }
            // Take care of floating-point precision:
            if (!equalsArray(coordsA, coordsB)) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|" 
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        System.out.println("testIterator: " + n + " segments.");
    }

    static void testFlattening(Path2D pathA, Path2D pathB) {
        // - get a flattened PathIterator using "getPathIterator(flatness,)" and make sure it works
        // (optional - compare to original path if the original was already flat)
        // (but, also run it on a path with curves to make sure flattening engine didn't break)
        final PathIterator itA = pathA.getPathIterator(at, FLATNESS);
        final PathIterator itB = pathB.getPathIterator(at, FLATNESS);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + "type are not equals [" + typeA + "|" + typeB + "] !");
            }
            // Take care of floating-point precision:
            if (!equalsArray(coordsA, coordsB)) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|" 
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        System.out.println("testFlattening: " + n + " segments.");
    }

    static void testAddMove(Path2D pathA) {
        addMove(pathA);
        System.out.println("testAddMove: passed.");
    }

    static void testAddLine(Path2D pathA, boolean isEmpty) {
        try {
            addLinesOnly(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                System.out.println("testAddLine: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        System.out.println("testAddLine: passed.");
    }

    static void testAddQuad(Path2D pathA, boolean isEmpty) {
        try {
            addCurvesOnly(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                System.out.println("testAddQuad: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        System.out.println("testAddQuad: passed.");
    }

    static void testGetBounds(Path2D pathA, Path2D pathB) {
        // - get the bounds of the cloned path
        final Rectangle rA = pathA.getBounds();
        final Rectangle rB = pathB.getBounds();

        if (!rA.equals(rB)) {
            throw new IllegalStateException("Bounds are not equals [" + rA 
                + "|" + rB + "] !");
        }
        final Rectangle2D r2dA = pathA.getBounds2D();
        final Rectangle2D r2dB = pathB.getBounds2D();

        if (!equalsRectangle2D(r2dA, r2dB)) {
            throw new IllegalStateException("Bounds2D are not equals [" 
                + r2dA + "|" + r2dB + "] !");
        }
        System.out.println("testGetBounds: passed.");
    }

    static void testTransform(Path2D pathA) {
        // - use the transform() method on the cloned path
        pathA.transform(at);
        System.out.println("testTransform: passed.");
    }

    static void testIntersect(Path2D pathA, Path2D pathB) {
        // - call intersects(points), intersects(rect) on a cloned path
        boolean resA = pathA.intersects(rect2d);
        boolean resB = pathB.intersects(rect2d);
        if (resA != resB) {
            throw new IllegalStateException("Intersects(rect2d) are not equals ["
                + resA + "|" + resB + "] !");
        }
        resA = pathA.intersects(1.0, 2.0, 13.0, 17.0);
        resB = pathB.intersects(1.0, 2.0, 13.0, 17.0);
        if (resA != resB) {
            throw new IllegalStateException("Intersects(doubles) are not equals ["
                + resA + "|" + resB + "] !");
        }
        System.out.println("testIntersect: passed.");
    }

    static void testContains(Path2D pathA, Path2D pathB) {
        // - call contains(pt), contains(rect) on a cloned path
        boolean resA = pathA.contains(pt2d);
        boolean resB = pathB.contains(pt2d);
        if (resA != resB) {
            throw new IllegalStateException("Contains(pt) are not equals [" 
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(pt2d.getX(), pt2d.getY());
        resB = pathB.contains(pt2d.getX(), pt2d.getY());
        if (resA != resB) {
            throw new IllegalStateException("Contains(x,y) are not equals [" 
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(rect2d);
        resB = pathB.contains(rect2d);
        if (resA != resB) {
            throw new IllegalStateException("Contains(rect2d) are not equals [" 
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(1.0, 2.0, 13.0, 17.0);
        resB = pathB.contains(1.0, 2.0, 13.0, 17.0);
        if (resA != resB) {
            throw new IllegalStateException("Contains(doubles) are not equals [" 
                + resA + "|" + resB + "] !");
        }
        System.out.println("testContains: passed.");
    }

    static void testGetCurrentPoint(Path2D pathA, Path2D pathB) {
        // - call getCurrentPoint() on the cloned path
        final Point2D ptA = pathA.getCurrentPoint();
        final Point2D ptB = pathA.getCurrentPoint();
        if (((ptA == null) && (ptB != null))
            || ((ptA != null) && !ptA.equals(ptB)))
        {
            throw new IllegalStateException("getCurrentPoint() are not equals [" 
                + ptA + "|" + ptB + "] !");
        }
        System.out.println("testGetCurrentPoint: passed.");
    }

    // Custom equals methods ---
    static boolean equalsArray(float[] a, float[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }

        int length = a.length;
        if (a2.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!equals(a[i], a2[i])) {
                return false;
            }
        }

        return true;
    }

    static boolean equalsRectangle2D(Rectangle2D a, Rectangle2D b) {
        if (a == b) {
            return true;
        }
        return equals(a.getX(), b.getX())
            && equals(a.getY(), b.getY())
            && equals(a.getWidth(), b.getWidth())
            && equals(a.getHeight(), b.getHeight());
    }

    static boolean equals(float a, float b) {
        return (Math.abs(a - b) <= EPSILON);
    }

    static boolean equals(double a, double b) {
        return (Math.abs(a - b) <= EPSILON);
    }

}
