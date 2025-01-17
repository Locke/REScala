package kofre.datatypes

import kofre.base.{Bottom, Lattice}
import kofre.dotted.{DotMap, Dotted, DottedLattice, HasDots}
import kofre.syntax.{OpsSyntaxHelper}
import kofre.time.Dots

/** A GMap (Grow-only Map) is a Delta CRDT that models a map from an arbitrary key type to nested Delta CRDTs.
  * In contrast to [[ObserveRemoveMap]], key/value pairs cannot be removed from this map. However, due to the smaller internal
  * representation, mutate operations on large maps are a lot faster than on ObserveRemoveMap.
  *
  * The nested CRDTs can be queried/mutated by calling the queryKey/mutateKey methods with a DeltaQuery/DeltaMutator generated
  * by a CRDT Interface method of the nested CRDT. For example, to enable a nested EWFlag, one would pass `EWFlagInterface.enable()`
  * as the DeltaMutator to mutateKey.
  */
type GrowOnlyMap[K, V] = Map[K, V]

object GrowOnlyMap {

  def empty[K, V]: GrowOnlyMap[K, V] = Map.empty

  given [K, V]: Bottom[GrowOnlyMap[K, V]] = Bottom.mapBottom

  given lattice[K, V: Lattice]: Lattice[GrowOnlyMap[K, V]] = Lattice.mapLattice
  given contextLattice[K, V: DottedLattice: HasDots: Bottom]: DottedLattice[GrowOnlyMap[K, V]] =
    DotMap.dottedLattice[K, V].contextbimap[Map[K, V]](_.map(_.repr), _.map(DotMap.apply))

  extension [C, K, V](container: C)
    def growOnlyMap: syntax[C, K, V] = syntax(container)

  implicit class syntax[C, K, V](container: C)
      extends OpsSyntaxHelper[C, GrowOnlyMap[K, V]](container) {

    def contains(using PermQuery)(k: K): Boolean = current.contains(k)

    def queryKey(using PermQuery)(k: K): Option[V] = current.get(k)

    def queryAllEntries(using PermQuery)(): Iterable[V] = current.values

    def mutateKeyNamedCtx(k: K, default: => V)(m: Dotted[V] => Dotted[V])(using
        PermCausalMutate,
    ): C = {
      m(
        queryKey(k).getOrElse(default).inheritContext
      ).map(v => Map(k -> v)).mutator
    }
  }

}
