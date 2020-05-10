package test;

/**
 * Basic ulp scaling test
 */
public class Precision {
    /* epsilon value to roughly evaluate Math.ulp((float) x) ~ 1e-7 */
    private final static double EPS = 1e-7d;

    public static void main(String[] unused) {
        for (double d = 1e-6; d < 1e10; d = 10.0 * d) {
            System.out.println("ulp(" + ((float) d) + ") = " + Math.ulp((float) d));
            System.out.println("ulp(" + d + ") = " + Math.ulp(d));
            System.out.println("ratio = " + (Math.ulp((float) d) / Math.ulp(d)));
            System.out.println("rough ulp(" + ((float) d) + ") = " + (float)Math.abs(EPS * d));
            System.out.println("ratio rough = " + (((float)Math.abs(EPS * d)) / Math.ulp((float) d)));
        }
    }

    private Precision() {
        super();
    }
}
