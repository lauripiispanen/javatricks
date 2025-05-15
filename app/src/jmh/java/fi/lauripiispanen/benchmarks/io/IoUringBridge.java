package fi.lauripiispanen.benchmarks.io;

public class IoUringBridge {
  static {
    try {
      System.loadLibrary("io_uring_reader");
    } catch (UnsatisfiedLinkError e) {
      System.err.println("io_uring native library not loaded: " + e.getMessage());
    }
  }

  public static native int readOffsets(String filePath, long[] offsets, int chunkSize, byte[] buffer);
}