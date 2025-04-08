# javatricks

This repository contains various experiments exploring the internal workings and performance characteristics of Java.

## Experiment 1: False Sharing

This experiment investigates the phenomenon of "false sharing" in multi-threaded Java applications.

### False Sharing?

False Sharing occurs when multiple threads on different CPU cores modify variables that reside on the same cache line. Even though the threads might be accessing logically distinct variables, the underlying cache coherence protocol forces the cache line to be invalidated and re-fetched across cores unnecessarily. This leads to significant performance degradation due to increased cache misses and bus traffic.

Modern CPUs fetch data from main memory in blocks called cache lines (typically 64 bytes). When a thread modifies a variable, the entire cache line containing that variable is marked as dirty. If another thread on a different core needs to access *any* variable on that same cache line (even a different one), the cache line must be synchronized between the cores, often involving flushing the line from one cache and fetching it from memory or another cache.

### Demonstration with JMH

We used a simple JMH (Java Microbenchmark Harness) benchmark to demonstrate the performance impact of false sharing. The benchmark measures the throughput of multiple threads incrementing `volatile long` counters.

Three scenarios were tested:

1.  **Shared:** Threads increment counters located sequentially in memory, likely causing false sharing.
2.  **Padded:** Manual padding is added between counters to ensure they reside on separate cache lines.
3.  **Contended:** The `@jdk.internal.vm.annotation.Contended` annotation is used, letting the JVM handle the padding automatically.

**Benchmark Results:**

Manual padding w/ two threads:

```
FalseSharingBenchmark.shared                        thrpt   10      61605,080 ±      697,841  ops/ms
FalseSharingBenchmark.shared:threadOne              thrpt   10      30859,425 ±      402,547  ops/ms
FalseSharingBenchmark.shared:threadTwo              thrpt   10      30745,655 ±      370,248  ops/ms
FalseSharingBenchmark.sharedPadded                  thrpt   10     319110,855 ±     8636,750  ops/ms
FalseSharingBenchmark.sharedPadded:threadOnePadded  thrpt   10     159559,089 ±     4354,915  ops/ms
FalseSharingBenchmark.sharedPadded:threadTwoPadded  thrpt   10     159551,766 ±     4301,505  ops/ms
```

Manual padding vs. `@Contended` annotation w/ four threads:
```
Benchmark                                                    Mode  Cnt       Score       Error   Units
FalseSharingBenchmark.shared                                thrpt   10  120935,457 ±   716,220  ops/ms
FalseSharingBenchmark.shared:threadFour                     thrpt   10   30378,396 ±   232,613  ops/ms
FalseSharingBenchmark.shared:threadOne                      thrpt   10   29994,905 ±   455,190  ops/ms
FalseSharingBenchmark.shared:threadThree                    thrpt   10   30521,759 ±   392,599  ops/ms
FalseSharingBenchmark.shared:threadTwo                      thrpt   10   30040,397 ±   444,160  ops/ms
FalseSharingBenchmark.sharedContended                       thrpt   10  613333,027 ± 22605,389  ops/ms
FalseSharingBenchmark.sharedContended:threadFourContended   thrpt   10  153282,115 ±  5865,765  ops/ms
FalseSharingBenchmark.sharedContended:threadOneContended    thrpt   10  153458,037 ±  5502,959  ops/ms
FalseSharingBenchmark.sharedContended:threadThreeContended  thrpt   10  153226,867 ±  5869,162  ops/ms
FalseSharingBenchmark.sharedContended:threadTwoContended    thrpt   10  153366,008 ±  5472,848  ops/ms
FalseSharingBenchmark.sharedPadded                          thrpt   10  383142,212 ± 24388,322  ops/ms
FalseSharingBenchmark.sharedPadded:threadFourPadded         thrpt   10   96042,353 ±  6447,167  ops/ms
FalseSharingBenchmark.sharedPadded:threadOnePadded          thrpt   10   95592,055 ±  6876,214  ops/ms
FalseSharingBenchmark.sharedPadded:threadThreePadded        thrpt   10   95542,397 ±  6824,755  ops/ms
FalseSharingBenchmark.sharedPadded:threadTwoPadded          thrpt   10   95965,407 ±  6313,346  ops/ms
```

As the results show, the `shared` benchmark has significantly lower throughput compared to the `sharedPadded` and `sharedContended` versions, which is exacerbated by adding more threads to increase contention. This highlights the performance penalty of false sharing. Both manual padding and using `@Contended` yield substantial improvements (around 5x higher total throughput) by ensuring variables accessed by different threads reside on separate cache lines.

Increasing manual padding shows that performance gets closer to `@Contended`:

```
FalseSharingBenchmark.sharedPadded                          thrpt   10  580073,970 ± 38916,332  ops/ms
FalseSharingBenchmark.sharedPadded:threadFourPadded         thrpt   10  145043,245 ±  9716,278  ops/ms
FalseSharingBenchmark.sharedPadded:threadOnePadded          thrpt   10  144860,255 ±  9816,440  ops/ms
FalseSharingBenchmark.sharedPadded:threadThreePadded        thrpt   10  144986,332 ±  9852,272  ops/ms
FalseSharingBenchmark.sharedPadded:threadTwoPadded          thrpt   10  145184,139 ±  9543,492  ops/ms
```

### Assembly Inspection

Looking at the compiled assembly code confirms the memory layout differences:

**Unpadded (`shared`):**

The offset between accesses is small (#0x10, or 16 bytes), indicating the variables are close together in memory, likely within the same cache line.

```assembly
0x0000000147ac81a8:   add	x11, x10, #0x10       ; pointer to field `value1`
0x0000000147ac81ac:   ldar	x11, [x11]            ; atomic load
0x0000000147ac81b0:   add	x11, x11, #0x1        ; increment
0x0000000147ac81b4:   add	x12, x10, #0x10
0x0000000147ac81b8:   stlr	x11, [x12]            ; atomic store (volatile)
```

**Padded (`sharedPadded`):**

The offset is much larger (#0x50, or 80 bytes), exceeding the typical cache line size (64 bytes), thus placing the variables on different cache lines.

```assembly
0x0000000147ac81c8:   add	x12, x11, #0x50       ; pointer to field `value1`
0x0000000147ac81cc:   ldar	x12, [x12]            ; atomic load
0x0000000147ac81d0:   add	x12, x12, #0x1
0x0000000147ac81d4:   add	x13, x11, #0x50
0x0000000147ac81d8:   stlr	x12, [x13]            ; atomic store
```

*(Note: The `@Contended` annotation achieves a similar separation, managed by the JVM.)*

### Conclusion

While rarely an issue in most production systems, false sharing can severely impact the performance of highly optimized multi-threaded applications. Developers should be aware of memory layout and utilize padding techniques (manually or via `@Contended`) when contended access to variables located close in memory is expected.


### Running the experiments

Run the benchmark

```
./gradlew jmh
```

Output compiled assembly.

```
java -cp app/build/classes/java/jmh/ -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -XX:+PrintCompilation -Djava.library.path=[path-to-hsdis-dylib-dir] -XX:CompileCommand=compileonly,fi.lauripiispanen.benchmarks.CompilationMain::doIt  fi.lauripiispanen.benchmarks.CompilationMain > output.asm
```