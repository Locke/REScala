package kofre.predef

import kofre.base.DecomposeLattice
import kofre.base.Defs.Id
import kofre.syntax.AllPermissionsCtx.withID
import kofre.syntax.{AllPermissionsCtx, ArdtOpsContains, FixedIdCtx, IdentifierCtx, OpsSyntaxHelper, QueryCtx}

case class PosNegCounter(pos: GrowOnlyCounter, neg: GrowOnlyCounter) derives DecomposeLattice

/** A PNCounter (Positive-Negative Counter) is a Delta CRDT modeling a counter.
  *
  * It is composed of two grow-only counters (see [[GCounterInterface]]) to enable both increments and decrements of the counter value.
  */
object PosNegCounter {

  val zero: PosNegCounter = PosNegCounter(GrowOnlyCounter.zero, GrowOnlyCounter.zero)

  implicit class PNCounterSyntax[C](container: C)(using ArdtOpsContains[C, PosNegCounter])
      extends OpsSyntaxHelper[C, PosNegCounter](container) {
    def value(using QueryP): Int =
      val pos = current._1.value
      val neg = current._2.value
      pos - neg

    def inc()(using MutationIDP): C =
      val pos = current._1.inc()(using withID(replicaID))
      PosNegCounter(pos, GrowOnlyCounter.zero)

    def dec()(using MutationIDP): C =
      val neg = current._2.inc()(using withID(replicaID))
      PosNegCounter(GrowOnlyCounter.zero, neg)

    def add(delta: Int)(using MutationIDP): C = {
      if (delta > 0) PosNegCounter(current.pos.inc(delta)(using withID(replicaID)), GrowOnlyCounter.zero)
      else if (delta < 0) PosNegCounter(GrowOnlyCounter.zero, current.neg.inc(-delta)(using withID(replicaID)))
      else PosNegCounter.zero
    }
  }
}
