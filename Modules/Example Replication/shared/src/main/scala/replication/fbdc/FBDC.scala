package replication.fbdc

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import de.rmgk.options.{Argument, Style}
import kofre.base.Lattice.optionLattice
import kofre.base.{Bottom, Lattice, Uid}
import kofre.datatypes.CausalQueue.QueueElement
import kofre.datatypes.alternatives.ObserveRemoveSet
import kofre.datatypes.*
import kofre.datatypes.alternatives.lww.TimedVal
import kofre.dotted.{Dotted, DottedLattice, HasDots}
import kofre.syntax.{DeltaBuffer, PermCausalMutate, ReplicaId}
import kofre.time.{Dots, VectorClock}
import loci.communicator.tcp.TCP
import loci.registry.Registry
import replication.DataManager
import replication.JsoniterCodecs.given

import java.nio.file.Path
import java.util.Timer
import scala.annotation.nowarn
import scala.reflect.ClassTag

enum Req:
  def executor: Uid
  case Fortune(executor: Uid)
  case Northwind(executor: Uid, query: String)
enum Res:
  def req: Req
  case Fortune(req: Req.Fortune, result: String)
  case Northwind(req: Req.Northwind, result: List[Map[String, String]])

class Focus[Inner: DottedLattice, Outer](dm: DataManager[Outer])(extract: Outer => Inner, wrap: Inner => Outer) {

  type Cont[T] = DeltaBuffer[Dotted[T]]

  type Mod[T, C] = PermCausalMutate[C, T] ?=> C => C

  def apply(using pcm: PermCausalMutate[Dotted[Outer], Outer])(fun: Mod[Inner, Cont[Inner]]): Unit = {
    dm.transform { outer =>
      val resBuffer = fun(DeltaBuffer(outer.map(extract)))
      resBuffer.deltaBuffer.reduceLeftOption(_ merge _) match
        case None        => outer
        case Some(delta) => pcm.mutateContext(outer, delta.map(wrap))
    }
  }
}

type RespValue = Option[TimedVal[Res]]
given Ordering[VectorClock] = VectorClock.vectorClockTotalOrdering

given DottedLattice[RespValue] = DottedLattice.liftLattice

given HasDots[RespValue] = HasDots.noDots

case class State(
    requests: CausalQueue[Req],
    responses: ObserveRemoveMap[String, RespValue],
    providers: ObserveRemoveMap[Uid, AddWinsSet[String]]
) derives DottedLattice, HasDots, Bottom

object State:
  extension (dm: DataManager[State])
    def modReq          = Focus(dm)(_.requests, d => Bottom.empty.copy(requests = d))
    def modRes          = Focus(dm)(_.responses, d => Bottom.empty.copy(responses = d))
    def modParticipants = Focus(dm)(_.providers, d => Bottom.empty.copy(providers = d))

class FbdcExampleData {
  val replicaId = Uid.gen()
  val registry  = new Registry

  val dataManager =
    @nowarn given JsonValueCodec[State] = JsonCodecMaker.make(CodecMakerConfig.withMapAsArray(true))
    new DataManager[State](replicaId, registry)

  def addCapability(capability: String) =
    dataManager.modParticipants { part =>
      part.observeRemoveMap.mutateKey(replicaId)(_.add(using replicaId)(capability))
    }

  val requests = dataManager.mergedState.map(_.data.requests.values)
  val myRequests =
    val r = requests.map(_.filter(_.value.executor == replicaId))
    r.observe { reqs =>
      if reqs.nonEmpty
      then
        dataManager.modReq { aws =>
          aws.removeBy { (req: Req) => req.executor == replicaId }
        }
    }
    r
  val responses = dataManager.mergedState.map(_.data.responses.entries.toMap)

  val latestFortune = responses.map(_.get("fortune").flatten.map(_.payload).collect {
    case res: Res.Fortune => res
  })

  val latestNorthwind = responses.map(_.get("northwind").flatten.map(_.payload).collect {
    case res: Res.Northwind => res
  })

  def requestsOf[T: ClassTag] = myRequests.map(_.collect {
    case req @ QueueElement(x: T, _, _) => req.copy(value = x)
  })

  val providers = dataManager.mergedState.map(_.data.providers)

}
