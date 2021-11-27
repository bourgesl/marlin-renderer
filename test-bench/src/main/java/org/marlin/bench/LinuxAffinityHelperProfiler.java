package org.marlin.bench;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.profile.ProfilerException;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;

/**
 * Basic Profiler that adjust Forked VM to use 'taskset -c CORE_ID'
 * @author bourgesl
 */
public class LinuxAffinityHelperProfiler implements ExternalProfiler {

    private static final boolean TRACE = false;

    private static String CPU_CORE_IDS = null;

    private static String getCpuCoreIds() throws ProfilerException {
        if (CPU_CORE_IDS != null) {
            return CPU_CORE_IDS;
        }
        final String envCoreIds = System.getenv("CPU_CORE_IDS");
        if (envCoreIds == null || envCoreIds.isEmpty()) {
            throw new ProfilerException("Missing environment variable 'coreIds' !");
        }
        CPU_CORE_IDS = envCoreIds;

        if (TRACE) {
            System.out.println("AffinityProfiler: CPU_CORE_IDS='" + CPU_CORE_IDS + "'");
        }
        return envCoreIds;
    }

    public LinuxAffinityHelperProfiler() throws ProfilerException {
        getCpuCoreIds();
    }

    @Override
    public Collection<String> addJVMInvokeOptions(BenchmarkParams params) {
        return new ArrayList<>(Arrays.asList("taskset", "-c", CPU_CORE_IDS));
    }

    @Override
    public Collection<String> addJVMOptions(BenchmarkParams params) {
        return Collections.emptyList();
    }

    @Override
    public void beforeTrial(BenchmarkParams params) {
        // do nothing
    }

    @Override
    public Collection<? extends Result> afterTrial(BenchmarkResult br, long pid, File stdOut, File stdErr) {
        return Collections.emptyList();
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Linux Affinity Helper";
    }
}
