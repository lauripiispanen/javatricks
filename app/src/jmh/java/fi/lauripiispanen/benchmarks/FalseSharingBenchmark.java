package fi.lauripiispanen.benchmarks;

import org.openjdk.jmh.annotations.*;

import fi.lauripiispanen.benchmarks.state.SharedState;
import fi.lauripiispanen.benchmarks.state.SharedStatePadded;
import fi.lauripiispanen.benchmarks.state.SharedStateContended;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread) // Separate benchmark instance per thread
public class FalseSharingBenchmark {

    // Shared object between threads
    static final SharedState sharedState = new SharedState();
    static final SharedStatePadded sharedStatePadded = new SharedStatePadded();
    static final SharedStateContended sharedStateContended = new SharedStateContended();

    @Benchmark
    @Group("shared")
    @GroupThreads(1)
    public void threadOne() {
        sharedState.value1++;
    }

    @Benchmark
    @Group("shared")
    @GroupThreads(1)
    public void threadTwo() {
        sharedState.value2++;
    }

    @Benchmark
    @Group("shared")
    @GroupThreads(1)
    public void threadThree() {
        sharedState.value3++;
    }

    @Benchmark
    @Group("shared")
    @GroupThreads(1)
    public void threadFour() {
        sharedState.value4++;
    }

    @Benchmark
    @Group("sharedPadded")
    @GroupThreads(1)
    public void threadOnePadded() {
        sharedStatePadded.value1++;
    }

    @Benchmark
    @Group("sharedPadded")
    @GroupThreads(1)
    public void threadTwoPadded() {
        sharedStatePadded.value2++;
    }

    @Benchmark
    @Group("sharedPadded")
    @GroupThreads(1)
    public void threadThreePadded() {
        sharedStatePadded.value3++;
    }

    @Benchmark
    @Group("sharedPadded")
    @GroupThreads(1)
    public void threadFourPadded() {
        sharedStatePadded.value4++;
    }

    @Benchmark
    @Group("sharedContended")
    @GroupThreads(1)
    public void threadOneContended() {
        sharedStateContended.value1++;
    }

    @Benchmark
    @Group("sharedContended")
    @GroupThreads(1)
    public void threadTwoContended() {
        sharedStateContended.value2++;
    }

    @Benchmark
    @Group("sharedContended")
    @GroupThreads(1)
    public void threadThreeContended() {
        sharedStateContended.value3++;
    }

    @Benchmark
    @Group("sharedContended")
    @GroupThreads(1)
    public void threadFourContended() {
        sharedStateContended.value4++;
    }
}