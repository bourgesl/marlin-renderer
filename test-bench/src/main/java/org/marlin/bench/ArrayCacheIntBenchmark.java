package org.marlin.bench;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
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
import sun.java2d.marlin.TestArrayCacheInt;
import sun.java2d.marlin.TestArrayCacheInt.ContextWidenArray;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(10)

public class ArrayCacheIntBenchmark {

    public final static String PARAM_SIZE = "arraySize";

    private final static boolean DEBUG = true;

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        @Param({
            "10000",
            "100000",
            "1000000",
            "16777217"
        })
        int arraySize;
    }

    @State(Scope.Thread)
    public static class ThreadState {

        BenchmarkState bs = null;
        ContextWidenArray ctx = null;

        @Setup(Level.Iteration)
        public void setUpIteration(final BenchmarkState bs) {
            this.bs = bs;
            this.ctx = new ContextWidenArray();
        }
    }

    @Benchmark
    public int sort(final ThreadState ts) {
        return TestArrayCacheInt.testWidenArray(ts.ctx, ts.bs.arraySize);
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
                    .include(ArrayCacheIntBenchmark.class.getSimpleName());

            // Autotune start small
            builder.verbosity(VerboseMode.NORMAL)
                    .shouldFailOnError(true)
                    .addProfiler(LinuxAffinityHelperProfiler.class);

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
