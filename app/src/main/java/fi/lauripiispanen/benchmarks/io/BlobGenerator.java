package fi.lauripiispanen.benchmarks.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class BlobGenerator {

  public static void main(String[] args) throws Exception {
    File dir = new File("blobs");
    if (!dir.exists())
      dir.mkdirs();

    int count = 1_000_000;
    int minSize = 4096 * 1024 * 1024;
    int maxSize = 16384 * 1024 * 1024;
    Random random = new Random(42);

    for (int i = 0; i < count; i++) {
      File f = new File(dir, "blob_" + i + ".json");
      System.out.println("Generating " + f.getAbsolutePath());
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
        int targetSize = minSize + random.nextInt(maxSize - minSize + 1);
        int written = 0;

        while (written < targetSize) {
          String deviceId = "dev_" + (random.nextInt(10_000));
          long ts = System.currentTimeMillis();
          double val = random.nextDouble() * 100;

          String json = String.format("{\"device\":\"%s\",\"ts\":%d,\"val\":%.2f}\n",
              deviceId, ts, val);
          byte[] content = json.getBytes(StandardCharsets.UTF_8);

          if (written + content.length > targetSize) {
            int remaining = targetSize - written;
            out.write(content, 0, remaining); // clip to fit
            written += remaining;
          } else {
            out.write(content);
            written += content.length;
          }
        }
      }

      if (i % 1000 == 0) {
        System.out.printf("Generated %d/%d files\r", i, count);
      }
    }

    System.out.println("\nGenerated " + count + " files in " + dir.getAbsolutePath());
  }
}
