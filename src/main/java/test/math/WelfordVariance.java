package test.math;

/**
 * Simple implementation of Welford's algorithm for
 * online-computation of the variance of a stream.
 *
 * see http://jonisalonen.com/2013/deriving-welfords-method-for-computing-variance/
 *
 * @author Sebastian Wild (wild@uwaterloo.ca)
 */
public final class WelfordVariance {

    private long nSamples;
    private double min, max, mean, squaredError;

    public WelfordVariance() {
        reset();
    }

    public void reset() {
        nSamples = 0L;
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        mean = 0.0;
        squaredError = 0.0;
    }

    public void copy(final WelfordVariance other) {
        nSamples = other.nSamples;
        min = other.min;
        max = other.max;
        mean = other.mean;
        squaredError = other.squaredError;
    }

    public void add(final double x) {
        if (x < min) {
            min = x;
        }
        if (x > max) {
            max = x;
        }
        final double oldMean = mean;
        mean += (x - mean) / (++nSamples);
        squaredError += (x - mean) * (x - oldMean);
    }

    public long nSamples() {
        return nSamples;
    }

    public double min() {
        if (nSamples != 0L) {
            return min;
        }
        return Double.NaN;
    }

    public double max() {
        if (nSamples != 0L) {
            return max;
        }
        return Double.NaN;
    }

    public double mean() {
        if (nSamples != 0L) {
            return mean;
        }
        return Double.NaN;
    }

    public double variance() {
        if (nSamples != 0L) {
            return squaredError / (nSamples - 1L);
        }
        return Double.NaN;
    }

    public double stddev() {
        if (nSamples != 0L) {
            return Math.sqrt(variance());
        }
        return Double.NaN;
    }

    public double rms() {
        if (nSamples != 0L) {
            return mean() + stddev();
        }
        return Double.NaN;
    }

    public double rawErrorPercent() {
        if (nSamples != 0L) {
            return (100.0 * stddev() / mean());
        }
        return Double.NaN;
    }

    public double total() {
        if (nSamples != 0L) {
            return mean() * nSamples;
        }
        return Double.NaN;
    }

    @Override
    public String toString() {
        return "[" + nSamples()
                + ": µ=" + mean()
                + " σ=" + stddev()
                + " (" + rawErrorPercent()
                + " %) rms=" + rms()
                + " min=" + min()
                + " max=" + max()
                + " sum=" + total()
                + "]";
    }

    public static void main(String[] args) {
        double[] values;

        values = new double[]{1, 2, 2, 2, 3, 3, 4, 4, 4, 4, 4, 5, 5, 6, 6, 7, 8, 89, 10000, 100001, 00, 101};
        test(values);

        final int N = 1000;

        double nHigh = 1E15;
        double nLow = 1;
        values = new double[N];

        for (int i = 0; i < N; i++) {
            values[i] = (i % 2 == 0) ? nHigh : nLow;
        }
        test(values);
        System.out.println("Excepted mean = " + (nHigh + nLow) / 2.0);
        System.out.println("Excepted total = " + (nHigh + nLow) * (N / 2));

        nHigh = 1.0;
        nLow = 1E-15;
        values = new double[N];

        for (int i = 0; i < N; i++) {
            values[i] = (i % 2 == 0) ? nHigh : nLow;
        }
        test(values);
        System.out.println("Excepted mean = " + (nHigh + nLow) / 2.0);
        System.out.println("Excepted total = " + (nHigh + nLow) * (N / 2));
    }

    private static void test(final double[] values) {
        final WelfordVariance v = new WelfordVariance();
        for (double val : values) {
            v.add(val);
        }
        System.out.println("v.mean() = " + v.mean());
        System.out.println("v.variance() = " + v.variance());
        System.out.println("v.stdev() = " + v.stddev());
        System.out.println("stats() = " + v);

        System.out.println("naiveSum = " + naiveSum(values));
        System.out.println("kahanSum = " + kahanSum(values));
        System.out.println("---");
    }

    private static double naiveSum(double[] values) {
        final double[] state = new double[1]; // sum
        state[0] = 0.0;
        for (int i = 0; i < values.length; i++) {
            state[0] += values[i];
        }
        return state[0];
    }

    private static double kahanSum(double[] values) {
        final double[] state = new double[2]; // sum | error
        state[0] = 0.0;
        state[1] = 0.0;
        for (int i = 0; i < values.length; i++) {
            final double y = values[i] - state[1];
            final double t = state[0] + y;
            state[1] = (t - state[0]) - y;
            state[0] = t;
        }
        return state[0];
    }
}
