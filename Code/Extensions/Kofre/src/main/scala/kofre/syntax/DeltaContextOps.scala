package kofre.syntax

import kofre.Defs.Id
import kofre.decompose.interfaces.GCounterInterface.GCounter
import kofre.{Defs, Lattice}

trait QueryCtx[C, L]:
  def query(c: C): L
trait MutateCtx[C, L] extends QueryCtx[C, L]:
  def mutate(c: C, delta: L): C
trait IdentifierCtx[C]:
  def replicaId(c: C): Id
class FixedIdCtx[C](id: Id) extends IdentifierCtx[C]:
  override def replicaId(c: C): Id = id
trait AllPermissionsCtx[C, L] extends IdentifierCtx[C], MutateCtx[C, L]

object QueryCtx:
  given identityQuery[A]: QueryCtx[A, A] = MutateCtx.identityDeltaMutate
object MutateCtx:
  given identityDeltaMutate[A]: MutateCtx[A, A] with
    override def query(c: A): A = c
    override def mutate(c: A, delta: A): A = delta
object AllPermissionsCtx:
  def withID[C, L](id: Id)(using mctx: MutateCtx[C, L]): AllPermissionsCtx[C, L] = new AllPermissionsCtx[C, L]:
    def mutate(c: C, delta: L): C = mctx.mutate(c, delta)
    def replicaId(c: C): Id = id
    def query(c: C): L = mctx.query(c)

/** Helps to define operations that update any container [[C]] containing values of type [[L]]
  * using a scheme where mutations return deltas which are systematically applied.
  */
trait OpsSyntaxHelper[C, L](container: C) {
  final type MutationIDP = AllPermissionsCtx[C, L]
  final type QueryP      = QueryCtx[C, L]
  final type MutationP   = MutateCtx[C, L]

  final type MutationID = MutationIDP ?=> C
  final type Mutation   = MutationP ?=> C
  final type Query[T]   = QueryP ?=> T

  final protected def current(using perm: QueryCtx[C, L]): L                  = perm.query(container)
  final protected def replicaID(using perm: IdentifierCtx[C]): Defs.Id        = perm.replicaId(container)
  final protected given mutate(using perm: MutateCtx[C, L]): Conversion[L, C] = perm.mutate(container, _)
}