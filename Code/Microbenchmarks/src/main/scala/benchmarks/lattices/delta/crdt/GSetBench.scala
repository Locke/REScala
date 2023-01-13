package benchmarks.lattices.delta.crdt

import org.openjdk.jmh.annotations.*
import kofre.datatypes.GrowOnlySet.*
import kofre.syntax.{DeltaBuffer, DeltaBufferDotted}

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
class GSetBench {
  @Param(Array("0", "1", "10", "100", "1000"))
  var size: Int = _

  var set: DeltaBuffer[Set[Int]] = _

  @Setup
  def setup(): Unit = {
    set = (0 until size).foldLeft(DeltaBuffer("a", Set.empty[Int])) {
      case (s, e) => s.insert(e)
    }
  }

  @Benchmark
  def elements(): Set[Int] = set.elements

  @Benchmark
  def insert(): DeltaBuffer[Set[Int]] = set.insert(-1)
}
