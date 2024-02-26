package benchmarks.reactor

import benchmarks.EngineParam
import org.openjdk.jmh.annotations._
import reactives.extra.reactor.ReactorBundle
import reactives.operator.Interface

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(5)
@Threads(1)
@State(Scope.Thread)
class ReadBenchmark {
  var engine: Interface       = scala.compiletime.uninitialized
  final lazy val stableEngine = engine
  final lazy val reactorApi   = new ReactorBundle[stableEngine.type](stableEngine)

  import reactorApi._
  import stableEngine._

  var reactor: Reactor[Int] = scala.compiletime.uninitialized
  var trigger: Evt[Unit]    = scala.compiletime.uninitialized

  @Setup
  def setup(engineParam: EngineParam) = {
    engine = engineParam.engine
    trigger = Evt[Unit]()
    reactor = Reactor.loop(0) {
      S.next(trigger) {
        S.read(_ => S.end)
      }
    }
  }

  @Benchmark
  def run(): Unit = trigger.fire()
}
