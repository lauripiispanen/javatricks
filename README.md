# javatricks

This repository contains various experiments exploring the internal workings and performance characteristics of Java.

## Experiment 1: False Sharing

<details>
<summary>
This experiment investigates the phenomenon of "false sharing" in multi-threaded Java applications.
</summary>

### False Sharing?

False Sharing occurs when multiple threads on different CPU cores modify variables that reside on the same cache line. Even though the threads might be accessing logically distinct variables, the underlying cache coherence protocol forces the cache line to be invalidated and re-fetched across cores unnecessarily. This leads to significant performance degradation due to increased cache misses and bus traffic.

Modern CPUs fetch data from main memory in blocks called cache lines (typically 64 bytes). When a thread modifies a variable, the entire cache line containing that variable is marked as dirty. If another thread on a different core needs to access *any* variable on that same cache line (even a different one), the cache line must be synchronized between the cores, often involving flushing the line from one cache and fetching it from memory or another cache.

### Demonstration with JMH

We used a simple JMH (Java Microbenchmark Harness) benchmark to demonstrate the performance impact of false sharing. The benchmark measures the throughput of multiple threads incrementing `volatile long` counters.

Three scenarios were tested:

1.  **Shared:** Threads increment counters located sequentially in memory, likely causing false sharing.
2.  **Padded:** Manual padding is added between counters to ensure they reside on separate cache lines.
3.  **Contended:** The `@jdk.internal.vm.annotation.Contended` annotation is used, letting the JVM handle the padding automatically.

**Benchmark Results (Apple M1 Max):**

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
 add	x11, x10, #0x10       ; pointer to field `value1`
 ldar	x11, [x11]            ; atomic load
 add	x11, x11, #0x1        ; increment
 add	x12, x10, #0x10
 stlr	x11, [x12]            ; atomic store (volatile)
```

**Padded (`sharedPadded`):**

The offset is much larger (#0x50, or 80 bytes), exceeding the typical cache line size (64 bytes), thus placing the variables on different cache lines.

```assembly
 add	x12, x11, #0x50       ; pointer to field `value1`
 ldar	x12, [x12]            ; atomic load
 add	x12, x12, #0x1
 add	x13, x11, #0x50
 stlr	x12, [x13]            ; atomic store
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
</details>

## Experiment 2: Division performance on modern CPUs

<details>
<summary>
This experiment investigates the performance implications of using modulo operations versus bitwise operations for hash table indexing and Bloom filter implementations.
</summary>

### Background

Modern CPUs are highly optimized for certain operations, with integer division and division operations being notably slower than bitwise operations. This experiment explores these performance characteristics in the context of hash table indexing and Bloom filter implementations.

### Benchmark Design

The benchmark suite tests several scenarios:

1. **Pure Hash Function Performance**
   - `hashWithModulo`: Using `Math.floorMod()` for hash value reduction
   - `hashWithBitwiseAnd`: Using bitwise AND with (size-1) for power-of-2 sizes
   - `hashWithoutMasking`: Raw hash computation without reduction

2. **Pure Indexing Performance**
   - `indexingWithModulo`: Using modulo for index calculation
   - `indexingWithBitwiseAnd`: Using bitwise AND for power-of-2 sizes

3. **Bloom Filter Implementations**
   - `bloomFilterInsertWithBitSet`: Using Java's `BitSet`
   - `bloomFilterInsertWithManualBitArray`: Manual bit array implementation
   - `bloomFilterInsertWithBooleanArray`: Using boolean array
   - `bloomFilterQueryWithModulo`: Query using modulo operations
   - `bloomFilterQueryWithBitwiseAnd`: Query using bitwise operations

### Results Analysis

The benchmark results (on Apple M1 Max) reveal several key insights:

1. **Hash Function Performance**
   - Bitwise AND is ~2.5x faster than modulo operations

2. **Indexing Performance**
   - Bitwise AND is dramatically faster (~20x) than modulo operations

3. **Bloom Filter Implementations**
   - Manual bit array implementation shows good performance for larger sizes
   - BitSet performance is competitive but slightly slower
   - Query performance with bitwise operations is ~2x faster than modulo

### Key Findings

1. **Power-of-2 Sizing**: When possible, using power-of-2 sizes for hash tables and Bloom filters allows for efficient bitwise operations instead of modulo.

2. **Implementation Choice**: The choice between BitSet, boolean array, or manual bit array depends on the use case:
   - Boolean arrays are fastest for small sizes
   - Manual bit arrays are more memory-efficient and perform well for larger sizes
   - BitSet provides a good balance of convenience and performance

3. **Modulo vs Bitwise**: The performance difference between modulo and bitwise operations is significant enough to warrant careful consideration in performance-critical code.

### Conclusion

The results demonstrate that careful consideration of CPU operation costs can lead to significant performance improvements. When implementing hash tables or Bloom filters:

1. Use power-of-2 sizes when possible to enable bitwise operations
2. Choose the appropriate bit storage implementation based on size requirements
3. Avoid modulo operations in performance-critical paths
4. Consider memory usage vs performance trade-offs when selecting implementations


**Benchmark Results (Apple M1 Max):**

```
Benchmark                                                   (bitsetSize)  (numElements)  Mode  Cnt          Score         Error  Units
CpuDivisionBenchmark.bitsetSetOnly                                131072          50000  avgt    3     105017,096 ±   19579,056  ns/op
CpuDivisionBenchmark.bitsetSetOnly                                131072         200000  avgt    3     416390,744 ±   43046,974  ns/op
CpuDivisionBenchmark.bitsetSetOnly                               1048576          50000  avgt    3     106044,957 ±    5715,036  ns/op
CpuDivisionBenchmark.bitsetSetOnly                               1048576         200000  avgt    3     426784,623 ±  181585,366  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBitSet                  131072          50000  avgt    3      53045,900 ±    3081,579  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBitSet                  131072         200000  avgt    3     210223,274 ±    9090,088  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBitSet                 1048576          50000  avgt    3      58811,558 ±   19257,124  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBitSet                 1048576         200000  avgt    3     215715,031 ±   19798,896  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBooleanArray            131072          50000  avgt    3      22519,256 ±   11065,114  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBooleanArray            131072         200000  avgt    3      76525,463 ±    5548,244  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBooleanArray           1048576          50000  avgt    3      57916,813 ±    6231,833  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithBooleanArray           1048576         200000  avgt    3     170518,844 ±    9453,143  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithManualBitArray          131072          50000  avgt    3      30963,484 ±   11203,913  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithManualBitArray          131072         200000  avgt    3     125916,582 ±   80444,330  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithManualBitArray         1048576          50000  avgt    3      35681,091 ±    5510,615  ns/op
CpuDivisionBenchmark.bloomFilterInsertWithManualBitArray         1048576         200000  avgt    3     127580,281 ±   10778,755  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd               131072          50000  avgt    3     721728,185 ±   28232,733  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:hits          131072          50000  avgt    3  129285342,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:misses        131072          50000  avgt    3  287414658,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd               131072         200000  avgt    3    4058572,571 ±  662439,443  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:hits          131072         200000  avgt    3  159689440,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:misses        131072         200000  avgt    3  136910560,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd              1048576          50000  avgt    3     596910,740 ±   14164,948  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:hits         1048576          50000  avgt    3  108509826,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:misses       1048576          50000  avgt    3  395390174,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd              1048576         200000  avgt    3    2860234,912 ±  994504,546  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:hits         1048576         200000  avgt    3  108479070,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithBitwiseAnd:misses       1048576         200000  avgt    3  312520930,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo                   131072          50000  avgt    3    1381439,369 ±  329748,746  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithModulo:hits              131072          50000  avgt    3   67528089,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo:misses            131072          50000  avgt    3  150121911,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo                   131072         200000  avgt    3    6348024,405 ± 4796321,227  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithModulo:hits              131072         200000  avgt    3  102188320,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo:misses            131072         200000  avgt    3   87611680,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo                  1048576          50000  avgt    3    1258727,845 ±  250752,948  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithModulo:hits             1048576          50000  avgt    3   51423192,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo:misses           1048576          50000  avgt    3  187376808,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo                  1048576         200000  avgt    3    5420811,450 ±  236169,868  ns/op
CpuDivisionBenchmark.bloomFilterQueryWithModulo:hits             1048576         200000  avgt    3   57202740,000                    #
CpuDivisionBenchmark.bloomFilterQueryWithModulo:misses           1048576         200000  avgt    3  164797260,000                    #
CpuDivisionBenchmark.booleanArraySetOnly                          131072          50000  avgt    3      17493,654 ±    6213,956  ns/op
CpuDivisionBenchmark.booleanArraySetOnly                          131072         200000  avgt    3      63263,283 ±   33397,812  ns/op
CpuDivisionBenchmark.booleanArraySetOnly                         1048576          50000  avgt    3      27120,142 ±    5999,949  ns/op
CpuDivisionBenchmark.booleanArraySetOnly                         1048576         200000  avgt    3      73456,586 ±   12864,248  ns/op
CpuDivisionBenchmark.hashWithBitwiseAnd                           131072          50000  avgt    3     454272,796 ±   74208,398  ns/op
CpuDivisionBenchmark.hashWithBitwiseAnd                           131072         200000  avgt    3    1926717,840 ±  277792,398  ns/op
CpuDivisionBenchmark.hashWithBitwiseAnd                          1048576          50000  avgt    3     454088,598 ±   14023,229  ns/op
CpuDivisionBenchmark.hashWithBitwiseAnd                          1048576         200000  avgt    3    1921004,156 ±  169911,789  ns/op
CpuDivisionBenchmark.hashWithModulo                               131072          50000  avgt    3    1172494,244 ±  145857,040  ns/op
CpuDivisionBenchmark.hashWithModulo                               131072         200000  avgt    3    4841181,904 ±  846031,439  ns/op
CpuDivisionBenchmark.hashWithModulo                              1048576          50000  avgt    3    1180231,025 ±  228614,130  ns/op
CpuDivisionBenchmark.hashWithModulo                              1048576         200000  avgt    3    4848234,477 ±  314440,020  ns/op
CpuDivisionBenchmark.hashWithoutMasking                           131072          50000  avgt    3     446747,639 ±   22707,669  ns/op
CpuDivisionBenchmark.hashWithoutMasking                           131072         200000  avgt    3    1849552,584 ±   64065,512  ns/op
CpuDivisionBenchmark.hashWithoutMasking                          1048576          50000  avgt    3     445367,970 ±   17998,347  ns/op
CpuDivisionBenchmark.hashWithoutMasking                          1048576         200000  avgt    3    1937974,709 ± 1375283,655  ns/op
CpuDivisionBenchmark.indexingWithBitwiseAnd                       131072          50000  avgt    3       5603,283 ±    1716,167  ns/op
CpuDivisionBenchmark.indexingWithBitwiseAnd                       131072         200000  avgt    3      22488,621 ±   11268,551  ns/op
CpuDivisionBenchmark.indexingWithBitwiseAnd                      1048576          50000  avgt    3       5625,997 ±    1261,428  ns/op
CpuDivisionBenchmark.indexingWithBitwiseAnd                      1048576         200000  avgt    3      23630,282 ±   47738,423  ns/op
CpuDivisionBenchmark.indexingWithModulo                           131072          50000  avgt    3     103227,632 ±   35723,276  ns/op
CpuDivisionBenchmark.indexingWithModulo                           131072         200000  avgt    3     543050,113 ±   93756,129  ns/op
CpuDivisionBenchmark.indexingWithModulo                          1048576          50000  avgt    3     104787,750 ±   39164,354  ns/op
CpuDivisionBenchmark.indexingWithModulo                          1048576         200000  avgt    3     539933,978 ±   91642,901  ns/op
CpuDivisionBenchmark.manualBitArraySetOnly                        131072          50000  avgt    3     109158,890 ±    8196,172  ns/op
CpuDivisionBenchmark.manualBitArraySetOnly                        131072         200000  avgt    3     435514,457 ±   52146,252  ns/op
CpuDivisionBenchmark.manualBitArraySetOnly                       1048576          50000  avgt    3     111102,541 ±   27053,753  ns/op
CpuDivisionBenchmark.manualBitArraySetOnly                       1048576         200000  avgt    3     438821,655 ±  146022,972  ns/op
```
</details>