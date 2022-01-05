package org.marlin.bench;

import java.io.IOException;
import java.util.Locale;

/**
 * Main program entry point
 */
public class TestBenchMain {

    public static void main(String[] argv) throws IOException {
        // Pre init:
        // Set the default locale to en-US locale (for Numerical Fields "." ",")
        Locale.setDefault(Locale.US);

        // ArrayCacheIntBenchmark.main(argv);
        AreaSubtractBenchmark.main(argv);
    }
}
