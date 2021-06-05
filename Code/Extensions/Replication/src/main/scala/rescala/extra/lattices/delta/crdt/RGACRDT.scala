package rescala.extra.lattices.delta.crdt

import rescala.extra.lattices.delta.DotStore.{DotFun, DotLess, DotPair}
import rescala.extra.lattices.delta._
import rescala.extra.lattices.delta.crdt.GListCRDT.GListAsUIJDLattice

import scala.annotation.tailrec

sealed trait RGANode[A]
case class Alive[A](v: TimedVal[A]) extends RGANode[A]
case class Dead[A]()                extends RGANode[A]

object RGANode {
  implicit def RGANodeAsUIJDLattice[A]: UIJDLattice[RGANode[A]] = new UIJDLattice[RGANode[A]] {
    override def leq(left: RGANode[A], right: RGANode[A]): Boolean = (left, right) match {
      case (Dead(), _)            => false
      case (_, Dead())            => true
      case (Alive(lv), Alive(rv)) => rv.laterThan(lv)
    }

    /** Decomposes a lattice state into its unique irredundant join decomposition of join-irreducible states */
    override def decompose(state: RGANode[A]): Set[RGANode[A]] = Set(state)

    override def bottom: RGANode[A] = throw new UnsupportedOperationException("RGANode does not have a bottom value")

    /** By assumption: associative, commutative, idempotent. */
    override def merge(left: RGANode[A], right: RGANode[A]): RGANode[A] = (left, right) match {
      case (Alive(lv), Alive(rv)) => Alive(UIJDLattice[TimedVal[A]].merge(lv, rv))
      case _                      => Dead()
    }
  }
}

object RGACRDT {
  type State[E, C] = Causal[DotPair[ForcedWriteCRDT.State[GListCRDT.State[Dot]], DotFun[RGANode[E]]], C]

  implicit val ForcedWriteAsUIJDLattice: UIJDLattice[ForcedWriteCRDT.State[GListCRDT.State[Dot]]] =
    ForcedWriteCRDT.ForcedWriteAsUIJDLattice[GListCRDT.State[Dot]]

  private def deltaState[E, C: CContext](
      fw: Option[ForcedWriteCRDT.State[GListCRDT.State[Dot]]] = None,
      df: Option[DotFun[RGANode[E]]] = None,
      cc: Option[C] = None
  ): State[E, C] = {
    val bottom = UIJDLattice[State[E, C]].bottom

    Causal(
      (
        fw.getOrElse(bottom.dotStore._1),
        df.getOrElse(bottom.dotStore._2)
      ),
      cc.getOrElse(bottom.cc)
    )
  }

  private def readRec[E, C: CContext](state: State[E, C], target: Int, counted: Int, skipped: Int): Option[E] =
    state match {
      case Causal((fw, df), _) =>
        GListCRDT.read(counted + skipped)(fw.value).flatMap { d =>
          df(d) match {
            case Dead() => readRec(state, target, counted, skipped + 1)
            case Alive(tv) =>
              if (counted == target) Some(tv.value)
              else readRec(state, target, counted + 1, skipped)
          }
        }
    }

  def read[E, C: CContext](i: Int): DeltaQuery[State[E, C], Option[E]] = readRec(_, i, 0, 0)

  def size[E, C: CContext]: DeltaQuery[State[E, C], Int] = {
    case Causal((_, df), _) =>
      df.values.count {
        case Dead()   => false
        case Alive(_) => true
      }
  }

  def toList[E, C: CContext]: DeltaQuery[State[E, C], List[E]] = {
    case Causal((fw, df), _) =>
      GListCRDT.toList(fw.value).flatMap(df.get).collect {
        case Alive(tv) => tv.value
      }
  }

  def sequence[E, C: CContext]: DeltaQuery[State[E, C], Long] = {
    case Causal((fw, _), _) => fw.counter
  }

  private def insertRec[E, C: CContext](
      replicaID: String,
      state: State[E, C],
      target: Int,
      counted: Int,
      skipped: Int
  ): Option[ForcedWriteCRDT.State[GListCRDT.State[Dot]]] =
    state match {
      case Causal((fw, df), cc) =>
        if (target == counted) {
          val m = GListCRDT.insert(counted + skipped, CContext[C].nextDot(cc, replicaID))
          Some(
            ForcedWriteCRDT.mutate(m)(replicaID, fw)
          )
        } else {
          GListCRDT.read(counted + skipped)(fw.value) flatMap { d =>
            df(d) match {
              case Dead()   => insertRec(replicaID, state, target, counted, skipped + 1)
              case Alive(_) => insertRec(replicaID, state, target, counted + 1, skipped)
            }
          }
        }
    }

  def insert[E, C: CContext](i: Int, e: E): DeltaMutator[State[E, C]] = {
    case (replicaID, state @ Causal(_, cc)) =>
      val nextDot = CContext[C].nextDot(cc, replicaID)

      insertRec(replicaID, state, i, 0, 0) match {
        case None => UIJDLattice[State[E, C]].bottom
        case Some(golistDelta) =>
          val dfDelta = DotFun[RGANode[E]].empty + (nextDot -> Alive(TimedVal(e, replicaID)))

          deltaState(
            fw = Some(golistDelta),
            df = Some(dfDelta),
            cc = Some(CContext[C].fromSet(Set(nextDot)))
          )
      }
  }

  private def insertAllRec[E, C: CContext](
      replicaID: String,
      state: State[E, C],
      target: Int,
      counted: Int,
      skipped: Int,
      dots: Iterable[Dot]
  ): Option[ForcedWriteCRDT.State[GListCRDT.State[Dot]]] =
    state match {
      case Causal((fw, df), _) =>
        if (target == counted) {
          val m = GListCRDT.insertAll(counted + skipped, dots)
          Some(
            ForcedWriteCRDT.mutate(m)(replicaID, fw)
          )
        } else {
          GListCRDT.read(counted + skipped)(fw.value) flatMap { d =>
            df(d) match {
              case Dead()   => insertAllRec(replicaID, state, target, counted, skipped + 1, dots)
              case Alive(_) => insertAllRec(replicaID, state, target, counted + 1, skipped, dots)
            }
          }
        }
    }

  def insertAll[E, C: CContext](i: Int, elems: Iterable[E]): DeltaMutator[State[E, C]] = {
    case (replicaID, state @ Causal(_, cc)) =>
      val nextDot = CContext[C].nextDot(cc, replicaID)

      val nextDots = List.iterate(nextDot, elems.size) {
        case Dot(c, r) => Dot(c + 1, r)
      }

      insertAllRec(replicaID, state, i, 0, 0, nextDots) match {
        case None => UIJDLattice[State[E, C]].bottom
        case Some(golistDelta) =>
          val dfDelta = DotFun[RGANode[E]].empty ++ (nextDots zip elems.map(e => Alive(TimedVal(e, replicaID))))

          deltaState(
            fw = Some(golistDelta),
            df = Some(dfDelta),
            cc = Some(CContext[C].fromSet(nextDots.toSet))
          )
      }
  }

  @tailrec
  private def updateRGANodeRec[E, C: CContext](
      state: State[E, C],
      i: Int,
      newNode: RGANode[E],
      counted: Int,
      skipped: Int
  ): State[E, C] =
    state match {
      case Causal((fw, df), _) =>
        GListCRDT.read(counted + skipped)(fw.value) match {
          case None => UIJDLattice[State[E, C]].bottom
          case Some(d) =>
            df(d) match {
              case Dead() => updateRGANodeRec(state, i, newNode, counted, skipped + 1)
              case Alive(_) =>
                if (counted == i)
                  deltaState(df = Some(DotFun[RGANode[E]].empty + (d -> newNode)))
                else
                  updateRGANodeRec(state, i, newNode, counted + 1, skipped)
            }
        }
    }

  private def updateRGANode[E, C: CContext](state: State[E, C], i: Int, newNode: RGANode[E]): State[E, C] =
    updateRGANodeRec(state, i, newNode, 0, 0)

  def update[E, C: CContext](i: Int, e: E): DeltaMutator[State[E, C]] =
    (replicaID, state) => updateRGANode(state, i, Alive(TimedVal(e, replicaID)))

  def delete[E, C: CContext](i: Int): DeltaMutator[State[E, C]] = (_, state) => updateRGANode(state, i, Dead[E]())

  private def updateRGANodeBy[E, C: CContext](
      state: State[E, C],
      cond: E => Boolean,
      newNode: RGANode[E]
  ): State[E, C] =
    state match {
      case Causal((_, df), _) =>
        val toUpdate = df.toList.collect {
          case (d, Alive(tv)) if cond(tv.value) => d
        }

        val dfDelta = DotFun[RGANode[E]].empty ++ toUpdate.map(_ -> newNode)

        deltaState(df = Some(dfDelta))
    }

  def updateBy[E, C: CContext](cond: E => Boolean, e: E): DeltaMutator[State[E, C]] =
    (replicaID, state) => updateRGANodeBy(state, cond, Alive(TimedVal(e, replicaID)))

  def deleteBy[E, C: CContext](cond: E => Boolean): DeltaMutator[State[E, C]] =
    (_, state) => updateRGANodeBy(state, cond, Dead[E]())

  def purgeTombstones[E, C: CContext](): DeltaMutator[State[E, C]] = (replicaID, state) =>
    state match {
      case Causal((fw, df), _) =>
        val toRemove = df.collect {
          case (dot, Dead()) => dot
        }.toSet

        val golistPurged = GListCRDT.without(fw.value, toRemove)

        deltaState(
          fw = Some(ForcedWriteCRDT.forcedWrite(golistPurged)(replicaID, fw)),
          cc = Some(CContext[C].fromSet(toRemove))
        )
    }

  def clear[E, C: CContext](): DeltaMutator[State[E, C]] = {
    case (_, Causal(_, cc)) =>
      deltaState(
        cc = Some(cc)
      )
  }
}