package benchmarks.lattices.delta

import kofre.datatypes.ReplicatedList
import kofre.time.{Dots, Dot}
import kofre.dotted.{DottedLattice, Dotted}
import org.openjdk.jmh.annotations
import org.openjdk.jmh.annotations._
import kofre.base.Uid.asId
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@annotations.State(Scope.Thread)
class DeltaMergeBench {

  @Param(Array("1", "10", "100", "1000"))
  var size: Long = _

  var fullState: Dotted[ReplicatedList[Long]]         = _
  var plusOneState: Dotted[ReplicatedList[Long]]      = _
  var plusOneDeltaState: Dotted[ReplicatedList[Long]] = _

  def makeCContext(replicaID: String): Dots = {
    val dots = (0L until size).map(Dot(replicaID.asId, _)).toSet
    Dots.from(dots)
  }

  @Setup
  def setup(): Unit = {
    val baseState: Dotted[ReplicatedList[Long]] = Dotted(ReplicatedList.empty)

    val deltaState: Dotted[ReplicatedList[Long]] =
      baseState.insertAll(using "".asId)(0, 0L to size)
    fullState = DottedLattice[ReplicatedList[Long]].merge(baseState, deltaState)

    plusOneDeltaState = fullState.insert(using "".asId)(0, size)
    plusOneState = DottedLattice[ReplicatedList[Long]].merge(fullState, plusOneDeltaState)
  }

  @Benchmark
  def fullMerge: Dotted[ReplicatedList[Long]] = {
    DottedLattice[ReplicatedList[Long]].merge(fullState, plusOneState)
  }

  @Benchmark
  def fullDiff: Option[Dotted[ReplicatedList[Long]]] = {
    DottedLattice[ReplicatedList[Long]].diff(fullState, plusOneState)
  }

  @Benchmark
  def deltaMerge: Dotted[ReplicatedList[Long]] = {
    DottedLattice[ReplicatedList[Long]].diff(fullState, plusOneDeltaState) match {
      case Some(stateDiff) =>
        DottedLattice[ReplicatedList[Long]].merge(fullState, stateDiff)
      case None => fullState
    }
  }

  @Benchmark
  def deltaMergeNoDiff: Dotted[ReplicatedList[Long]] = {
    DottedLattice[ReplicatedList[Long]].merge(fullState, plusOneDeltaState)
  }
}
