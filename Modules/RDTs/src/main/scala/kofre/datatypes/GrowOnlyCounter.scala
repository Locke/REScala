package kofre.datatypes

import kofre.base.Lattice.*
import kofre.base.{Bottom, Uid, Lattice}
import kofre.dotted.DottedLattice
import kofre.syntax.{OpsSyntaxHelper, PermQuery}

case class GrowOnlyCounter(inner: Map[Uid, Int]) derives Bottom

/** A GCounter is a Delta CRDT modeling an increment-only counter. */
object GrowOnlyCounter {
  def zero: GrowOnlyCounter = GrowOnlyCounter(Map.empty)

  given lattice: Lattice[GrowOnlyCounter] =
    given Lattice[Int] = math.max
    Lattice.derived

  given contextDecompose: DottedLattice[GrowOnlyCounter] = DottedLattice.liftLattice

  extension [C](container: C)
    def growOnlyCounter: syntax[C] = syntax(container)

  implicit class syntax[C](container: C)
      extends OpsSyntaxHelper[C, GrowOnlyCounter](container) {
    def value(using PermQuery): Int = current.inner.valuesIterator.sum

    def inc(): IdMutate =
      GrowOnlyCounter(Map(replicaId -> (current.inner.getOrElse(replicaId, 0) + 1))).mutator
    def add(amount: Int): IdMutate =
      require(amount >= 0, "may not decrease counter")
      GrowOnlyCounter(Map(replicaId -> (current.inner.getOrElse(replicaId, 0) + amount))).mutator
  }
}
