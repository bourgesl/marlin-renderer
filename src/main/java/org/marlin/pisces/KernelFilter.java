/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.marlin.pisces;

import java.util.Arrays;

/**
 *
 * @author llooo
 */
final class KernelFilter {

    private final static boolean doDebug = false;
    private final static boolean useGaussian = true;
    private final static boolean useHamming = false;
    private final static boolean useCubic = false; // buggy
    static int[][] sumFilterWeights = null;
    static int totalFilter = 0;
    static int thresholdFilter = 0;
    static int minScaleFilter_LG = 0;
    static int maxScaleFilter = 0;

    static {
        long start = System.nanoTime();
        prepareFilter();
        long end = System.nanoTime();

        System.out.println("kernel computation time = " + 1e-6d * (end - start) + " ms.");
    }

    static void prepareFilter() {
        /* mimic Renderer */
        int SUBPIXEL_LG_POSITIONS_X = PiscesRenderingEngine.getSubPixel_Log2_X();
        int SUBPIXEL_LG_POSITIONS_Y = PiscesRenderingEngine.getSubPixel_Log2_Y();
        int SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
        int SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);

        // TODO: support filters having different X/Y support:
        final int len = (SUBPIXEL_POSITIONS_X + SUBPIXEL_POSITIONS_Y) >> 1;

        System.out.println("prepareFilter [" + len + " x " + len + "]");

        final int half = len / 2;

        final int samples = 128;

        final int N = half * samples; // 16 to have intermediate points
//        final int N = len * 2; // 16 to have intermediate points

//        final double TWO_PI = 2d * Math.PI;

        final double[] weights = new double[N + 1];

        if (useGaussian) {
            final double nSigma = 1.8d;
            final double sigma = half / nSigma; // 2 sigma = 4 ie half filter width
            final double sigma22 = 2d * sigma * sigma;

            double step = (nSigma * sigma) / (N + 1); // TEST
            double x = 0d;

            for (int i = 0; i <= N; i++) {
                weights[i] = gauss(x * x, sigma22);
                x += step;
            }
        } else if (useHamming) {
//            double step = (Math.PI - Math.PI / 4d) / N; // GOOD = MORE SHARP THAN PISCES            

//            double step = (Math.PI + 0d) * (N - 1) / ( (N - 1) * (N) ); // Hamming like = TOO SHARP

            double step = (Math.PI - Math.PI / 8d) / N; // TEST
            double angle = Math.PI;

            for (int i = 0; i <= N; i++) {
                weights[i] = 0.54d - 0.46d * Math.cos(angle);
                angle += step;
            }
            /*
             final int L = N - 1; hamming
             for (int i = 0; i <= L; i++) {
             // wi = 0.54 - 0.46 x cos(2 pi * i / (N-1) ) for 0 <= i <= N - 1
             weights[i] = 0.54d - 0.46d * Math.cos(TWO_PI * i / L);
             }
             */
        } else if (useCubic) {
            // Filter does not work: negative weights => negative coverage => Exception !
            double step = (2d) / (N - 1); // TEST
            double x = 0d;

            for (int i = 0; i <= N; i++) {
                weights[i] = cubicMitchellNetravali(x);
                x += step;
            }

        } else {
            // box filter
            for (int i = 0; i <= N; i++) {
                weights[i] = 1d;
            }
        }
        if (doDebug) {
            System.out.println("kernel[" + N + "] = " + Arrays.toString(weights));
        }

        final double[] weights1D = new double[len];
        for (int i = 0, j; i < half; i++) {
            weights1D[i] = 0d;
            for (j = 0; j < samples; j++) {
                weights1D[i] += weights[N - (i * samples + j)];
//                System.out.println("sample = " + (N - (i * samples + j)));
            }
            weights1D[i] /= samples;
            weights1D[len - i - 1] = weights1D[i];
        }
        System.out.println("weights1D = " + Arrays.toString(weights1D));
        // weights8 = [0.1197691, 0.39785218, 0.77000004, 0.9899479, 0.9899479, 0.77000004, 0.39785218, 0.1197691]

        double[][] fWeights2D = multiply(weights1D, weights1D);
        /*
         * fWeights2D = 
         * [ [0.014344636, 0.047650397, 0.09222221, 0.118565165, 0.118565165, 0.09222221, 0.047650397, 0.014344636],
         *   [0.047650397, 0.15828636,  0.3063462,  0.39385295,  0.39385295,  0.3063462,  0.15828636,  0.047650397],
         *   [0.09222221,  0.3063462,   0.59290004, 0.76225996,  0.76225996,  0.59290004, 0.3063462,   0.09222221],
         *   [0.118565165, 0.39385295,  0.76225996, 0.97999686,  0.97999686,  0.76225996, 0.39385295,  0.118565165],
         *   [0.118565165, 0.39385295,  0.76225996, 0.97999686,  0.97999686,  0.76225996, 0.39385295,  0.118565165],
         *   [0.09222221,  0.3063462,   0.59290004, 0.76225996,  0.76225996,  0.59290004, 0.3063462,   0.09222221],
         *   [0.047650397, 0.15828636,  0.3063462,  0.39385295,  0.39385295,  0.3063462,  0.15828636,  0.047650397],
         *   [0.014344636, 0.047650397, 0.09222221, 0.118565165, 0.118565165, 0.09222221, 0.047650397, 0.014344636]]
         */

        /*
         * fWeights2D = 
         * [[0.1775782266965402, 0.27677691136607147, 0.36278693837099263, 0.41256199043435493, 0.41256199043435493, 0.36278693837099263, 0.27677691136607147, 0.1775782266965402],
         *  [0.27677691136607147, 0.4313899293309854, 0.5654468464642743, 0.6430272200802658, 0.6430272200802658, 0.5654468464642743, 0.4313899293309854, 0.27677691136607147],
         *  [0.36278693837099263, 0.5654468464642743, 0.741162726427669, 0.8428516501276588, 0.8428516501276588, 0.741162726427669, 0.5654468464642743, 0.36278693837099263],
         *  [0.41256199043435493, 0.6430272200802658, 0.8428516501276588, 0.9584924859173232, 0.9584924859173232, 0.8428516501276588, 0.6430272200802658, 0.41256199043435493],
         *  [0.41256199043435493, 0.6430272200802658, 0.8428516501276588, 0.9584924859173232, 0.9584924859173232, 0.8428516501276588, 0.6430272200802658, 0.41256199043435493],
         *  [0.36278693837099263, 0.5654468464642743, 0.741162726427669, 0.8428516501276588, 0.8428516501276588, 0.741162726427669, 0.5654468464642743, 0.36278693837099263],
         *  [0.27677691136607147, 0.4313899293309854, 0.5654468464642743, 0.6430272200802658, 0.6430272200802658, 0.5654468464642743, 0.4313899293309854, 0.27677691136607147],
         *  [0.1775782266965402, 0.27677691136607147, 0.36278693837099263, 0.41256199043435493, 0.41256199043435493, 0.36278693837099263, 0.27677691136607147, 0.1775782266965402]]
         */

        System.out.println("fWeights2D = " + Arrays.deepToString(fWeights2D));


        // test gaussian 2D:
        if (useGaussian) {
            fWeights2D = new double[len][len];

            final double sigma = 0.5d / Math.sqrt(2); // 2 sigma = 4 ie half filter width
            final double sigma22 = 2d * sigma * sigma;

            // i [0;7]
            for (int i = 0, j; i < len; i++) {
                double x = (0.5d + i - half) / len; // [-3.5;3.5] / 8 => ]-.5;.5[
                for (j = 0; j < len; j++) {
                    double y = (0.5d + j - half) / len;
                    double dist = x * x + y * y;
                    //double weight = Math.pow(2d, -4d * (dist)); // support = 1.5
                    // double weight = Math.pow(2d, -6d * (dist)); // support = 1.0 ?
                    fWeights2D[i][j] = gauss(dist, sigma22);
                    System.out.println("gauss(" + x + "," + y + ") = " + fWeights2D[i][j]);
                }
            }
            System.out.println("fWeights2D (gauss2D support ~ 1) = " + Arrays.deepToString(fWeights2D));
        }

        // Presummed tables by rows:
        final int lenP1 = len + 1;

        final float[][] fSumWeights = new float[len][lenP1]; // float[Y][X in [0,8]+1 = total row]
        float[] sumFilterWeightsY;

        double sumD;
        for (int i = 0, j; i < len; i++) {
            sumFilterWeightsY = fSumWeights[i];
            sumD = 0d;
            for (j = 0; j < len; j++) {
                sumFilterWeightsY[j] = (float) sumD;
                sumD += fWeights2D[i][j];
            }
            sumFilterWeightsY[len] = (float) sumD;
        }
        System.out.println("fSumWeights = " + Arrays.deepToString(fSumWeights));


        // normalize presummed tables as integer:
        final int upScaleInt = 1024;
        final double scale = upScaleInt / fWeights2D[0][0]; // min and 1/1024 precision

        final int[][] iSumWeights = new int[len][lenP1]; // float[Y][X in [0,8]+1 = total row]
        for (int i = 0, j; i < len; i++) {
            for (j = 0; j < lenP1; j++) {
                iSumWeights[i][j] = (int) Math.round(scale * fSumWeights[i][j]);
            }
        }
        System.out.println("iSumWeights (scaled) = " + Arrays.deepToString(iSumWeights));
        /*
         * iSumWeights (scaled) = 
         * [[1024.0, 3402.0, 6583.0, 8464.0, 8464.0, 6583.0, 3402.0, 1024.0],
         *  [3402.0, 11299.0, 21869.0, 28115.0, 28115.0, 21869.0, 11299.0, 3402.0],
         *  [6583.0, 21869.0, 42325.0, 54414.0, 54414.0, 42325.0, 21869.0, 6583.0],
         *  [8464.0, 28115.0, 54414.0, 69958.0, 69958.0, 54414.0, 28115.0, 8464.0],
         *  [8464.0, 28115.0, 54414.0, 69958.0, 69958.0, 54414.0, 28115.0, 8464.0],
         *  [6583.0, 21869.0, 42325.0, 54414.0, 54414.0, 42325.0, 21869.0, 6583.0],
         *  [3402.0, 11299.0, 21869.0, 28115.0, 28115.0, 21869.0, 11299.0, 3402.0],
         *  [1024.0, 3402.0, 6583.0, 8464.0, 8464.0, 6583.0, 3402.0, 1024.0]]
         */

        final int totalIdx = len;

        int sum = 0;
        for (int i = 0; i < len; i++) {
            sum += iSumWeights[i][totalIdx];
        }
        System.out.println("sum = " + sum);

        final int iScale = sum;
        // sum = 1481200.0

        /*
         * sumFilterWeights = 
         * [[1024, 4426, 11009, 19473, 27937, 34520, 37922, 38946], 
         *  [3402, 14701, 36570, 64685, 92800, 114669, 125968, 129370], 
         *  [6583, 28452, 70777, 125191, 179605, 221930, 243799, 250382], 
         *  [8464, 36579, 90993, 160951, 230909, 285323, 313438, 321902], 
         *  [8464, 36579, 90993, 160951, 230909, 285323, 313438, 321902], 
         *  [6583, 28452, 70777, 125191, 179605, 221930, 243799, 250382], 
         *  [3402, 14701, 36570, 64685, 92800, 114669, 125968, 129370], 
         *  [1024, 4426, 11009, 19473, 27937, 34520, 37922, 38946]]
         */

        /*
         * sumFilterWeights = 
         * [[1024, 4426, 11009, 19473, 27937, 34520, 37921, 38945],
         *  [3402, 14701, 36570, 64685, 92800, 114669, 125969, 129370],
         *  [6583, 28452, 70777, 125191, 179605, 221930, 243798, 250382],
         *  [8464, 36579, 90994, 160951, 230909, 285323, 313439, 321903],
         *  [8464, 36579, 90994, 160951, 230909, 285323, 313439, 321903],
         *  [6583, 28452, 70777, 125191, 179605, 221930, 243798, 250382],
         *  [3402, 14701, 36570, 64685, 92800, 114669, 125969, 129370],
         *  [1024, 4426, 11009, 19473, 27937, 34520, 37921, 38945]]
         */

        /* 16 samples:
         * sumFilterWeights = 
         * [[1024, 4065, 9942, 17786, 25629, 31506, 34547, 35571], 
         *  [3041, 12074, 29527, 52823, 76119, 93572, 102604, 105646], 
         *  [5876, 23329, 57053, 102066, 147079, 180802, 198255, 204132], 
         *  [7844, 31140, 76153, 136236, 196320, 241333, 264629, 272473], 
         *  [7844, 31140, 76153, 136236, 196320, 241333, 264629, 272473], 
         *  [5876, 23329, 57053, 102066, 147079, 180802, 198255, 204132], 
         *  [3041, 12074, 29527, 52823, 76119, 93572, 102604, 105646], 
         *  [1024, 4065, 9942, 17786, 25629, 31506, 34547, 35571]]
         */

        /*
         * 128 samples:
         * sumFilterWeights = 
         * [[1024, 4027, 9827, 17601, 25374, 31174, 34177, 35201], 
         *  [3003, 11811, 28822, 51620, 74417, 91428, 100236, 103239], 
         *  [5800, 22811, 55664, 99693, 143722, 176575, 193586, 199386], 
         *  [7773, 30571, 74600, 133607, 192615, 236644, 259442, 267215], 
         *  [7773, 30571, 74600, 133607, 192615, 236644, 259442, 267215], 
         *  [5800, 22811, 55664, 99693, 143722, 176575, 193586, 199386], 
         *  [3003, 11811, 28822, 51620, 74417, 91428, 100236, 103239], 
         *  [1024, 4027, 9827, 17601, 25374, 31174, 34177, 35201]]
         */

        if (doDebug) {
            double tot = 0d;
            for (int i = 0; i < len; i++) {
                int sumRow = iSumWeights[i][totalIdx];
                float scaledSumRow = ((float) iSumWeights[i][totalIdx]) / iScale;
                tot += scaledSumRow;
                System.out.println("i = " + i + " sumRow = " + sumRow + " normalized sumWeights[i][8] = " + scaledSumRow);
            }
            System.out.println("tot = " + tot);
        }

        // Define constants:
        sumFilterWeights = iSumWeights;
        totalFilter = iScale;

        // Fix scale to have 256 levels:
        thresholdFilter = Math.max(upScaleInt, totalFilter >> 8); /* integer division by 256 */
        minScaleFilter_LG = (int) (Math.log(thresholdFilter) / Math.log(2d));
        maxScaleFilter = iScale >> minScaleFilter_LG;

        System.out.println("sumFilterWeights = " + Arrays.deepToString(sumFilterWeights));
        System.out.println("totalFilter      = " + totalFilter);
        System.out.println("thresholdFilter    = " + thresholdFilter);
        System.out.println("minScaleFilter_LG = " + minScaleFilter_LG);
        System.out.println("maxScaleFilter    = " + maxScaleFilter);

    }

    static double[][] multiply(double[] a, double[] b) {
        final int rowsInA = a.length;
        final int columnsInB = b.length;
        final double[][] c = new double[rowsInA][columnsInB];

        for (int i = 0, j; i < rowsInA; i++) {
            for (j = 0; j < columnsInB; j++) {
                c[i][j] = a[i] * b[j];
            }
        }
        return c;
    }

    static double cubicMitchellNetravali(final double x) {
        return MitchellNetravali(x, 1d / 3d, 1d / 3d);
    }

    static double cubicBSpline(final double x) {
        return MitchellNetravali(x, 1d, 0d);
    }

    static double cubicCatmullRom(final double x) {
        return MitchellNetravali(x, 0d, 1d / 2d);
    }

    // Mitchell Netravali Reconstruction Filter
    // B = 1,   C = 0   - cubic B-spline
    // B = 1/3, C = 1/3 - recommended
    // B = 0,   C = 1/2 - Catmull-Rom spline
    static double MitchellNetravali(final double x, double B, double C) {
        double ax = Math.abs(x);
        if (ax < 1d) {
            return ((12d - 9d * B - 6d * C) * ax * ax * ax
                    + (-18d + 12d * B + 6d * C) * ax * ax
                    + (6d - 2d * B)) / 6d;
        }
        if (ax < 2d) {
            return ((-B - 6d * C) * ax * ax * ax
                    + (6d * B + 30d * C) * ax * ax
                    + (-12d * B - 48d * C) * ax
                    + (8d * B + 24d * C)) / 6d;
        }
        return 0d;
    }

    final static double gauss(final double distance2, final double sigma22) {
        return Math.exp(-distance2 / sigma22);
    }

    /*    
     final static double filterSupport = 2d;

     static double[] getKernel(final int nSubPixels) {
     final double filterScale = 1f / nSubPixels;
     final double filterWidth = filterSupport / filterScale;

     final int right = (int) filterWidth;

     final double[] weights = new double[right + 1];

     for (int i = 0; i <= right; i++) {
     weights[i] = filterScale * cubicMitchellNetravali(filterScale * i); // cubicCatmullRom(x)
     }
     return weights;
     }
     */
    public static void main(String[] args) {
        System.out.println("KernelFilter loaded.");

        // System.out.println("kernel = " + Arrays.toString(getKernel(8)));
    }
}
