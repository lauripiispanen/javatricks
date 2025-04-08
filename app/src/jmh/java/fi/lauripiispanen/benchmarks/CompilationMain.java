package fi.lauripiispanen.benchmarks;

import fi.lauripiispanen.benchmarks.state.SharedState;
import fi.lauripiispanen.benchmarks.state.SharedStatePadded;

public class CompilationMain {

  static final SharedState sharedState = new SharedState();
  static final SharedStatePadded sharedStatePadded = new SharedStatePadded();

  static volatile long dummy1;
  static volatile long dummy2;

  public static void doIt() {
    sharedState.value1++;
    sharedStatePadded.value1++;

    dummy1 = sharedState.value1;
    dummy2 = sharedStatePadded.value1;
  }

  public static void main(String[] args) {
    for (int i = 0; i < 100_000; i++) {
      doIt();
    }
  }
}
