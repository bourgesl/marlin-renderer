/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.marlin.pisces;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 *
 * @author bourgesl
 */
public class TestHelpers {

    private final static int N = 10;

    /** scientific formatter */
    private final static NumberFormat fmt = new DecimalFormat("0.0##E0");

    public static void main(String[] args) {
        test();
    }

    static void test() {
        // small coefficients: d: -6.2942505E-5 a: -2.796301 b: 28.725681 c: -20.000006

        /*
    // find the roots of g(t) = d*t^3 + a*t^2 + b*t + c in [A,B)
    static int cubicRootsInAB(final float d0, float a0, float b0, float c0,
                              final float[] pts, final int off,
                              final float A, final float B)
         */
        final float[] ts = new float[3];

/*
// quad solver => low precision
t:   0.7511686
f(t) -1,907E-6

// cubic solver => miss precision
t:   0.75112474
f(t) -1,076E-3

*/
        float d = -6.2942505E-5f / 100f;
        float a = -2.796301f;
        float b = 28.725681f;
        float c = -20.000006f;

        if (Math.abs(d) < 1e-4f) {
            System.out.println("small coefficients: d: " + d + " a: " + a + " b: " + b + " c: " + c);
        }

        int num = Helpers.cubicRootsInAB(d, a, b, c, ts, 0, 0.0f, 1.0f);

        for (int i = 0; i < num; i++) {
            final float t = ts[i];
            final float ft = Helpers.evalCubic(d, a, b, c, t);

            System.out.println("t:   " + t);
            System.out.println("f(t) " + fmt.format(ft));

            for (int j = -N; j <= N; j++) {
                float u = t + j * Math.ulp(t);
                final float fu = Helpers.evalCubic(d, a, b, c, u);

                System.out.println(j + " ulps");
                System.out.println("u:  " + u);
                System.out.println("f(u): " + fmt.format(fu));
            }
        }
    }

}
