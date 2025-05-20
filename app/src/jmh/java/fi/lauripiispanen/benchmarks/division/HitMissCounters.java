package fi.lauripiispanen.benchmarks.division;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.AuxCounters.Type;

@State(Scope.Thread)
@AuxCounters(Type.EVENTS)
public class HitMissCounters {
  /** increment when you see a hit (e.g. bit was already set) */
  public long hits;

  /** increment when you see a miss (e.g. bit was not set) */
  public long misses;
}