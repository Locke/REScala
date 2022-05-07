package kofre.decompose.interfaces

import kofre.causality.CausalContext
import kofre.decompose.*
import kofre.syntax.OpsSyntaxHelper
import kofre.decompose.DecomposableDotStore.DotFun
import kofre.decompose.interfaces.LWWRegisterInterface.LWWRegister
import kofre.dotbased.WithContext

/** An MVRegister (Multi-Value Register) is a Delta CRDT modeling a register.
  *
  * In the absence of concurrent writes, the MVRegister is either empty or holds one value.
  * When multiple values are written concurrently, reading the MVRegister returns a set holding all these values.
  */
object MVRegisterInterface {
  type MVRegister[A] = WithContext[DotFun[A]]

  implicit class MVRegisterSyntax[C, A](container: C) extends OpsSyntaxHelper[C, MVRegister[A]](container) {

    def read(using QueryP): Set[A] = current.store.values.toSet

    def write(v: A)(using MutationIDP): C = {
      val nextDot = current.context.nextDot(replicaID)

      WithContext(
        Map(nextDot -> v),
        CausalContext.fromSet(current.store.keySet + nextDot)
      )
    }

    def clear()(using MutationIDP, UIJDLattice[MVRegister[A]]): C =
      WithContext(
        UIJDLattice[MVRegister[A]].empty.store,
        CausalContext.fromSet(current.store.keySet)
        )
  }
}
