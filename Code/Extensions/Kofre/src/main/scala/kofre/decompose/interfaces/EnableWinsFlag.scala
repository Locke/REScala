package kofre.decompose.interfaces

import kofre.base.DecomposeLattice
import kofre.causality.CausalContext
import kofre.decompose.*
import kofre.syntax.OpsSyntaxHelper
import kofre.contextual.ContextDecompose.*
import kofre.contextual.{ContextDecompose, WithContext}
import kofre.predef.Epoche

/** An EWFlag (Enable-Wins Flag) is a Delta CRDT modeling a boolean flag.
  *
  * When the flag is concurrently disabled and enabled then the enable operation wins, i.e. the resulting flag is enabled.
  */
case class EnableWinsFlag(inner: CausalContext) {
  export inner.*
}

object EnableWinsFlag {

  given latticeEWF: ContextDecompose[EnableWinsFlag] = ContextDecompose.product1ContextDecompose[CausalContext, EnableWinsFlag]

  /** It is enabled if there is a value in the store.
    * It relies on the external context to track removals.
    */
  implicit class EnableWinsFlagOps[C](container: C) extends OpsSyntaxHelper[C, EnableWinsFlag](container) {
    def read(using QueryP): Boolean = !current.isEmpty

    def enable()(using IdentifierP, QueryP, CausalMutation, CausalP): C = {
      val nextDot = context.nextDot(replicaID)
      WithContext(
        EnableWinsFlag(CausalContext.single(nextDot)),
        current add nextDot
      )
    }
    def disable()(using QueryP, CausalMutation): C = {
      WithContext(
        EnableWinsFlag(CausalContext.empty),
        current.inner
      )
    }
  }

}
