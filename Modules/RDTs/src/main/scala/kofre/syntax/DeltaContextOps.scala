package kofre.syntax

import kofre.base.Uid
import kofre.dotted.Dotted
import kofre.time.Dots

import scala.annotation.implicitNotFound

/** The basic idea behind this machinery is to allow lattices of type L to be stored in a Container of type C.
  * In the simplest case C = L and the lattice is used as is.
  * More complex containers contain additional information such as the replica ID
  * or a set of deltas since the last synchronization.
  * No matter the concrete container, they should all offer the same API to the underlying lattice.
  */

@implicitNotFound(
  "Requires query permission. If the syntax is incorrect, try specifying it explicitly:\n  container:  »${C}«\n  syntax for: »${L}«"
)
trait PermQuery[C, L]:
  def query(c: C): L
object PermQuery:
  given identityQuery[A]: PermQuery[A, A] = PermMutate.identityDeltaMutate

@implicitNotFound("Requires mutation permission.\nUnsure to modify »${L}«\nwithin »${C}«")
trait PermMutate[C, L] extends PermQuery[C, L]:
  def mutate(c: C, delta: L): C
object PermMutate:
  given identityDeltaMutate[A]: PermMutate[A, A] with
    override def query(c: A): A            = c
    override def mutate(c: A, delta: A): A = delta

@implicitNotFound(
  "Requires a replica ID."
)
opaque type ReplicaId = Uid
object ReplicaId:
  given ordering: Ordering[ReplicaId]             = Uid.ordering
  extension (id: ReplicaId) def uid: Uid          = id
  def apply(id: Uid): ReplicaId                   = id
  inline given fromId: Conversion[Uid, ReplicaId] = identity
  def predefined(s: String): ReplicaId            = ReplicaId.fromId(Uid.predefined(s))
  def unwrap(id: ReplicaId): Uid                  = id
  def gen(): ReplicaId                            = Uid.gen()

@implicitNotFound(
  "Requires context mutation permission.\nUnsure how to extract context from »${C}«\nto modify »${L}«"
)
trait PermCausalMutate[C, L] extends PermQuery[C, L]:
  def mutateContext(container: C, withContext: Dotted[L]): C
  def context(c: C): Dots

/** Helps to define operations that update any container [[C]] containing values of type [[L]]
  * using a scheme where mutations return deltas which are systematically applied.
  */
trait OpsTypes[C, L] {
  import kofre.syntax as s
  final type PermQuery        = s.PermQuery[C, L]
  final type PermMutate       = s.PermMutate[C, L]
  final type PermCausalMutate = s.PermCausalMutate[C, L]
  final type CausalMutate     = PermCausalMutate ?=> C
  final type Mutate           = PermMutate ?=> C
  final type IdMutate         = ReplicaId ?=> Mutate
}
class OpsSyntaxHelper[C, L](container: C) extends OpsTypes[C, L] {
  final protected[kofre] def current(using perm: PermQuery): L              = perm.query(container)
  final protected[kofre] def replicaId(using perm: ReplicaId): Uid          = perm.uid
  final protected[kofre] def context(using perm: PermCausalMutate): Dots    = perm.context(container)
  extension (l: L) def mutator: Mutate                                      = summon[PermMutate].mutate(container, l)
  extension (l: Dotted[L])(using perm: PermCausalMutate) def mutator: C     = perm.mutateContext(container, l)
  extension [A](a: A) def inheritContext(using PermCausalMutate): Dotted[A] = Dotted(a, context)

}
