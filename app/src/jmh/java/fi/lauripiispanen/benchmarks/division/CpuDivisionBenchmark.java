package fi.lauripiispanen.benchmarks.division;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.BitSet;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
public class CpuDivisionBenchmark {

  @Param({ "50000", "200000" })
  private int numElements;

  @Param({ "131072", "1048576" }) // Only power of 2 size to ensure correct semantics
  private int bitsetSize;

  private byte[][] testKeys;
  private int[] precomputedHashes;
  private Random random;
  private static final int KEY_LENGTH = 20;

  @Setup
  public void setup() {
    random = new Random(42);
    testKeys = new byte[numElements][];
    precomputedHashes = new int[numElements];

    // Generate 80% unique keys and 20% duplicates to simulate real-world caching
    int uniqueKeys = (int) (numElements * 0.8);

    // Generate unique keys
    for (int i = 0; i < uniqueKeys; i++) {
      testKeys[i] = generateRandomKey();
      precomputedHashes[i] = fnv1a64to32(testKeys[i]);
    }

    // Generate duplicates
    for (int i = uniqueKeys; i < numElements; i++) {
      int duplicateIndex = random.nextInt(uniqueKeys);
      testKeys[i] = testKeys[duplicateIndex];
      precomputedHashes[i] = precomputedHashes[duplicateIndex];
    }
  }

  private byte[] generateRandomKey() {
    byte[] key = new byte[KEY_LENGTH];
    random.nextBytes(key);
    return key;
  }

  private int fnv1a64to32(byte[] key) {
    long hash = 0xcbf29ce484222325L;
    for (byte b : key) {
      hash ^= (b & 0xff);
      hash *= 0x100000001b3L;
    }
    return (int) (hash ^ (hash >>> 32));
  }

  // Pure hash function benchmarks
  @Benchmark
  public void hashWithModulo(Blackhole bh) {
    for (byte[] key : testKeys) {
      int hash = fnv1a64to32(key);
      hash = Math.floorMod(hash, bitsetSize);
      bh.consume(hash);
    }
  }

  @Benchmark
  public void hashWithBitwiseAnd(Blackhole bh) {
    for (byte[] key : testKeys) {
      int hash = fnv1a64to32(key);
      hash = hash & (bitsetSize - 1);
      bh.consume(hash);
    }
  }

  @Benchmark
  public void hashWithoutMasking(Blackhole bh) {
    for (byte[] key : testKeys) {
      int hash = fnv1a64to32(key);
      bh.consume(hash);
    }
  }

  // Pure indexing benchmarks
  @Benchmark
  public void indexingWithModulo(Blackhole bh) {
    for (int hash : precomputedHashes) {
      int index = Math.floorMod(hash, bitsetSize);
      bh.consume(index);
    }
  }

  @Benchmark
  public void indexingWithBitwiseAnd(Blackhole bh) {
    for (int hash : precomputedHashes) {
      int index = hash & (bitsetSize - 1);
      bh.consume(index);
    }
  }

  // BitSet.set() benchmark
  @Benchmark
  public void bitsetSetOnly(Blackhole bh) {
    BitSet bs = new BitSet(bitsetSize);
    for (int i = 0; i < numElements; i++) {
      int index = i & (bitsetSize - 1);
      bs.set(index);
    }
    bh.consume(bs);
  }

  // Manual bit array set benchmark
  @Benchmark
  public void manualBitArraySetOnly(Blackhole bh) {
    long[] bits = new long[bitsetSize / 64];
    for (int i = 0; i < numElements; i++) {
      int index = i & (bitsetSize - 1);
      int word = index >>> 6; // divide by 64
      int bit = index & 63; // mod 64
      bits[word] |= (1L << bit);
    }
    bh.consume(bits);
  }

  // Boolean array control benchmark
  @Benchmark
  public void booleanArraySetOnly(Blackhole bh) {
    boolean[] bits = new boolean[bitsetSize];
    for (int i = 0; i < numElements; i++) {
      int index = i & (bitsetSize - 1);
      bits[index] = true;
    }
    bh.consume(bits);
  }

  // Full Bloom filter benchmarks with different implementations
  @Benchmark
  public void bloomFilterInsertWithBitSet(Blackhole bh) {
    BitSet bitset = new BitSet(bitsetSize);
    for (int hash : precomputedHashes) {
      int index = hash & (bitsetSize - 1);
      bitset.set(index);
    }
    bh.consume(bitset);
  }

  @Benchmark
  public void bloomFilterInsertWithManualBitArray(Blackhole bh) {
    long[] bits = new long[bitsetSize / 64];
    for (int hash : precomputedHashes) {
      int index = hash & (bitsetSize - 1);
      int word = index >>> 6; // divide by 64
      int bit = index & 63; // mod 64
      bits[word] |= (1L << bit);
    }
    bh.consume(bits);
  }

  @Benchmark
  public void bloomFilterInsertWithBooleanArray(Blackhole bh) {
    boolean[] bits = new boolean[bitsetSize];
    for (int hash : precomputedHashes) {
      int index = hash & (bitsetSize - 1);
      bits[index] = true;
    }
    bh.consume(bits);
  }

  @Benchmark
  public void bloomFilterQueryWithModulo(HitMissCounters counters, Blackhole bh) {
    long[] bits = new long[bitsetSize / 64];

    // Insert phase
    for (byte[] key : testKeys) {
      int hash = fnv1a64to32(key);
      int index = Math.floorMod(hash, bitsetSize);
      int word = index >>> 6;
      int bit = index & 63;
      boolean present = (bits[word] & (1L << bit)) != 0;
      if (present) {
        counters.hits++;
      } else {
        counters.misses++;
      }
      bits[word] |= (1L << bit);
    }

    bh.consume(counters);
  }

  @Benchmark
  public void bloomFilterQueryWithBitwiseAnd(HitMissCounters counters, Blackhole bh) {
    long[] bits = new long[bitsetSize / 64];

    // Insert phase
    for (byte[] key : testKeys) {
      int hash = fnv1a64to32(key);
      hash = hash & (bitsetSize - 1);
      int index = hash & (bitsetSize - 1);
      int word = index >>> 6;
      int bit = index & 63;
      boolean present = (bits[word] & (1L << bit)) != 0;
      if (present) {
        counters.hits++;
      } else {
        counters.misses++;
      }
      bits[word] |= (1L << bit);
    }

    bh.consume(counters);
  }

}
