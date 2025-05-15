package fi.lauripiispanen.benchmarks.io;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Future;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class IoBenchmark {

  static {
    System.loadLibrary("io_uring_reader"); // looks for libio_uring_reader.{so|dylib}
  }

  @Param({ "blobs" })
  public String blobDir;

  @Param({ "100" })
  public int numRandomReads;

  @Param({ "4" })
  public int numThreads;

  @Param({ "10240" })
  public int chunkSize;

  private File[] files;
  private ExecutorService executor;

  @Setup(Level.Trial)
  public void setup() {
    File dir = new File(blobDir);
    if (!dir.exists()) {
      throw new RuntimeException("Run BlobGenerator first");
    }
    files = dir.listFiles((d, name) -> name.endsWith(".json"));
    executor = Executors.newFixedThreadPool(numThreads);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
    }
  }

  @Setup(Level.Iteration)
  public void reshuffleFiles() {
    Collections.shuffle(Arrays.asList(files));
  }

  private long[] getRandomOffsets(long fileSize, int chunkSize, int numOffsets) {
    long[] offsets = new long[numOffsets];
    for (int i = 0; i < numOffsets; i++) {
      offsets[i] = ThreadLocalRandom.current().nextLong(0, Math.max(1, fileSize - chunkSize + 1));
    }
    return offsets;
  }

  @Benchmark
  @Threads(16)
  public int readWholeFile_FileInputStream() throws IOException {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];
    int total = 0;

    try (FileInputStream in = new FileInputStream(f)) {
      byte[] buf = new byte[1024]; // 1 KB buffer
      int read;
      while ((read = in.read(buf)) != -1) {
        // Fake processing: count commas (simulate parsing work)
        for (int i = 0; i < read; i++) {
          if (buf[i] == ',')
            total++;
        }
      }
    }

    return total;
  }

  @Benchmark
  @Threads(16)
  public int readWholeFile_AsynchronousFileChannel() throws Exception {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];

    try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
        Path.of(f.getAbsolutePath()),
        StandardOpenOption.READ)) {
      ByteBuffer buffer = ByteBuffer.allocate(1024);
      long position = 0;
      int total = 0;
      int bytesRead;

      while (true) {
        Future<Integer> future = channel.read(buffer, position);
        bytesRead = future.get(); // blocking here
        if (bytesRead == -1) {
          break;
        }
        buffer.flip();
        for (int i = 0; i < bytesRead; i++) {
          if (buffer.get() == ',')
            total++; // simulate processing
        }
        buffer.clear();
        position += bytesRead;
      }

      return total;
    }
  }

  @Benchmark
  @Threads(16)
  public int readWholeFile_MemoryMappedFile() throws IOException {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];
    int total = 0;

    try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
      long fileSize = channel.size();
      MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

      for (int i = 0; i < fileSize; i++) {
        if (buffer.get(i) == ',')
          total++;
      }
    }

    return total;
  }

  @Benchmark
  @Threads(16)
  public int randomRead_FileInputStream() throws IOException {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];
    int total = 0;
    long fileSize = f.length();
    long[] offsets = getRandomOffsets(fileSize, chunkSize, numRandomReads);

    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
      byte[] buf = new byte[chunkSize];
      for (long offset : offsets) {
        raf.seek(offset);
        int read = raf.read(buf, 0, (int) Math.min(chunkSize, fileSize - offset));
        for (int i = 0; i < read; i++) {
          if (buf[i] == ',')
            total++;
        }
      }
    }
    return total;
  }

  @Benchmark
  @Threads(16)
  public int randomRead_AsynchronousFileChannel() throws Exception {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];
    long fileSize = f.length();
    long[] offsets = getRandomOffsets(fileSize, chunkSize, numRandomReads);
    int total = 0;

    try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
        Path.of(f.getAbsolutePath()),
        StandardOpenOption.READ)) {
      ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
      for (long offset : offsets) {
        buffer.clear();
        int toRead = (int) Math.min(chunkSize, fileSize - offset);
        Future<Integer> future = channel.read(buffer, offset);
        int bytesRead = future.get();
        buffer.flip();
        for (int i = 0; i < Math.min(bytesRead, toRead); i++) {
          if (buffer.get() == ',')
            total++;
        }
      }
    }
    return total;
  }

  @Benchmark
  @Threads(16)
  public int randomRead_MemoryMappedFile() throws IOException {
    File f = files[ThreadLocalRandom.current().nextInt(files.length)];
    int total = 0;
    long fileSize = f.length();
    long[] offsets = getRandomOffsets(fileSize, chunkSize, numRandomReads);

    try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
      MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
      byte[] buf = new byte[chunkSize];
      for (long offset : offsets) {
        int toRead = (int) Math.min(chunkSize, fileSize - offset);
        buffer.position((int) offset);
        buffer.get(buf, 0, toRead);
        for (int i = 0; i < toRead; i++) {
          if (buf[i] == ',')
            total++;
        }
      }
    }
    return total;
  }

  @Benchmark
  public int randomRead_CompletableFuture() throws Exception {
    int threadCount = 16;
    List<CompletableFuture<Integer>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      File f = files[ThreadLocalRandom.current().nextInt(files.length)];
      long fileSize = f.length();
      long[] offsets = getRandomOffsets(fileSize, chunkSize, numRandomReads / threadCount);

      CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
        int total = 0;
        try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
            Path.of(f.getAbsolutePath()),
            StandardOpenOption.READ)) {

          ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
          for (long offset : offsets) {
            int toRead = (int) Math.min(chunkSize, fileSize - offset);

            buffer.clear();
            Future<Integer> readFuture = channel.read(buffer, offset);
            int bytesRead = readFuture.get();

            buffer.flip();
            for (int k = 0; k < Math.min(bytesRead, toRead); k++) {
              if (buffer.get() == ',')
                total++;
            }
          }
        } catch (Exception e) {
          throw new CompletionException(e);
        }
        return total;
      }, executor);

      futures.add(future);
    }

    // Wait for all futures and sum results
    return futures.stream()
        .map(CompletableFuture::join)
        .mapToInt(Integer::intValue)
        .sum();
  }
}