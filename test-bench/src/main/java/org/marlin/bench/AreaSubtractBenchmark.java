package org.marlin.bench;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Defaults;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.ProfilersFailedException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(5)
public class AreaSubtractBenchmark {

    private final static boolean TRACE = false;

    public final static String PARAM_SIZE = "length";

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        @Param({"10"})
        int length;

        private final static boolean USE_SEED_FIXED = true; // random seed fixed for reproducibility
        private final static long SEED_FIXED = 3447667858947863824L;

        // start (from Math.random)
        public static Random getRandom() {
            final long seed;
            if (USE_SEED_FIXED) {
                seed = SEED_FIXED;
            } else {
                seed = seedUniquifier() ^ System.nanoTime();
            }
            if (TRACE) {
                System.out.println("Random seed: " + seed);
            }
            return new Random(seed);
        }

        private static long seedUniquifier() {
            // L'Ecuyer, "Tables of Linear Congruential Generators of
            // Different Sizes and Good Lattice Structure", 1999
            for (;;) {
                long current = seedUniquifier.get();
                long next = current * 1181783497276652981L;
                if (seedUniquifier.compareAndSet(current, next)) {
                    return next;
                }
            }
        }

        private static final AtomicLong seedUniquifier
                                        = new AtomicLong(8682522807148012L);
        // end (from Math.random)
    }

    @State(Scope.Thread)
    public static class ThreadState {

        BenchmarkState bs = null;

        Rectangle rect = null;
        ArrayList<Ellipse> points = null;

        @Setup(Level.Iteration)
        public void setUpIteration(final BenchmarkState bs) {
            this.bs = bs;
            this.rect = new Rectangle(0, 0, 1.0, 1.0);
            rect.setFill(Color.BLACK);

            this.points = initPoints(bs.length);
        }

        private static ArrayList<Ellipse> initPoints(int len) {
            final Random random = BenchmarkState.getRandom(); // fixed seed for reproducibility

            final ArrayList<Ellipse> points = new ArrayList<>(len);

            for (int i = 0; i < len; i++) {
                // p in [0, 1]
                final double px = random.nextDouble();
                final double py = random.nextDouble();

                points.add(new Ellipse(px, py, 0.1, 0.05));
            }
            return points;
        }

    }

    @Benchmark
    public Object doClip(final ThreadState ts) {
        final Shape clip = computeComplexClip(ts.rect, ts.points, false);
        return clip;
    }

    @Benchmark
    public Object doClipTwice(final ThreadState ts) {
        final Shape clip = computeComplexClip(ts.rect, ts.points, true);
        return clip;
    }

    private Shape computeComplexClip(final Rectangle rect, final ArrayList<Ellipse> points, boolean twice) {
        Shape clip = rect;

        final double w = rect.getWidth();
        final double h = rect.getHeight();

        for (int i = 0; i < points.size(); i++) {
            final Ellipse ellipse = points.get(i);

            final Ellipse clipEllipse = new Ellipse();
            clipEllipse.setStrokeWidth(5.0);
            clipEllipse.setCenterX(ellipse.getCenterX() * w);
            clipEllipse.setCenterY(ellipse.getCenterY() * h);
            clipEllipse.setRadiusX(ellipse.getRadiusX() * w);
            clipEllipse.setRadiusY(ellipse.getRadiusY() * h);
            clipEllipse.setFill(Color.BLACK);

            if (TRACE) {
                System.out.println("Shape.subtract(): from " + clip + " with " + clipEllipse + " before");
            }

            clip = Shape.subtract(clip, clipEllipse);
            if (twice) {
                clip = Shape.subtract(clip, clipEllipse);
            }

            if (TRACE) {
                System.out.println("Shape.subtract(): to " + clip + " after");
            }
        }
        return clip;
    }

    /**
     * Custom main()
     */
    public static void main(String[] argv) throws IOException {
        final ChainedOptionsBuilder builder;

        // From org.openjdk.jmh.Main:
        try {
            final CommandLineOptions cmdOptions = new CommandLineOptions(argv);

            if (cmdOptions.shouldHelp()) {
                cmdOptions.showHelp();
                return;
            }

            if (cmdOptions.shouldListProfilers()) {
                cmdOptions.listProfilers();
                return;
            }

            if (cmdOptions.shouldListResultFormats()) {
                cmdOptions.listResultFormats();
                return;
            }

            final Runner runner = new Runner(cmdOptions);

            if (cmdOptions.shouldList()) {
                runner.list();
                return;
            }

            if (cmdOptions.shouldListWithParams()) {
                runner.listWithParams(cmdOptions);
                return;
            }

            // GO ...
            builder = new OptionsBuilder()
                    .parent(cmdOptions)
                    .include(AreaSubtractBenchmark.class.getSimpleName());

            // Autotune start small
            builder.verbosity(VerboseMode.NORMAL)
                    .shouldFailOnError(true);

        } catch (CommandLineOptionException e) {
            System.err.println(">> Error parsing command line:");
            System.err.println(" " + e.getMessage());
            System.exit(1);
            return;
        }

        final Options optionsBench = builder.build();

        final Runner runnerBench = new Runner(optionsBench);

        try {
            runnerBench.run();
        } catch (NoBenchmarksException e) {
            System.err.println("No matching benchmarks. Miss-spelled regexp?");

            if (optionsBench.verbosity().orElse(Defaults.VERBOSITY) != VerboseMode.EXTRA) {
                System.err.println("Use " + VerboseMode.EXTRA + " verbose mode to debug the pattern matching.");
            } else {
                runnerBench.list();
            }
            System.exit(1);
        } catch (ProfilersFailedException e) {
            // This is not exactly an error, set non-zero exit code
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (RunnerException e) {
            System.err.print("ERROR: ");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

}
