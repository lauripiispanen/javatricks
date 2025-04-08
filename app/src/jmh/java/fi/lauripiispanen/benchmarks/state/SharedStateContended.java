package fi.lauripiispanen.benchmarks.state;

import jdk.internal.vm.annotation.Contended;

public class SharedStateContended {
  @Contended
  public volatile long value1 = 0L;
  @Contended
  public volatile long value2 = 0L;
  @Contended
  public volatile long value3 = 0L;
  @Contended
  public volatile long value4 = 0L;
}
