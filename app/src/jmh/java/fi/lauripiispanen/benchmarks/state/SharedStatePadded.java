package fi.lauripiispanen.benchmarks.state;

public class SharedStatePadded {
  public volatile long value1 = 0L;
  // Add padding to separate cache lines
  public long p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14;
  public volatile long value2 = 0L;
  public long p15, p16, p17, p18, p19, p20, p21, p22, p23, p24, p25, p26, p27, p28;
  public volatile long value3 = 0L;
  public long p29, p30, p31, p32, p33, p34, p35, p36, p37, p38, p39, p40, p41, p42;
  public volatile long value4 = 0L;
  public long p43, p44, p45, p46, p47, p48, p49, p50, p51, p52, p53, p54, p55, p56;
}
