package kofre.datatypes

import kofre.base.{Bottom, Lattice}
import kofre.datatypes.AddWinsSet
import kofre.dotted.DottedLattice.*
import kofre.dotted.{DotMap, DotSet, Dotted, DottedLattice, HasDots}
import kofre.syntax.{OpsSyntaxHelper, ReplicaId}
import kofre.time.{Dot, Dots}

/** An AddWinsSet (Add-Wins Set) is a Delta CRDT modeling a set.
  *
  * When an element is concurrently added and removed/cleared from the set then the add operation wins, i.e. the resulting set contains the element.
  */
case class AddWinsSet[E](inner: DotMap[E, DotSet])

object AddWinsSet {

  def empty[E]: AddWinsSet[E] = AddWinsSet(DotMap.empty)

  given bottom[E]: Bottom[AddWinsSet[E]] with { override def empty: AddWinsSet[E] = AddWinsSet.empty }

  given contextDecompose[E]: DottedLattice[AddWinsSet[E]] = DottedLattice.derived
  given asCausalContext[E]: HasDots[AddWinsSet[E]]        = HasDots.derived

  extension [C, E](container: C)
    def addWinsSet: syntax[C, E] = syntax(container)

  implicit class syntax[C, E](container: C) extends OpsSyntaxHelper[C, AddWinsSet[E]](container) {

    def elements(using PermQuery): Set[E] = current.inner.repr.keySet

    def contains(using PermQuery)(elem: E): Boolean = current.inner.repr.contains(elem)

    def add(using ReplicaId)(e: E): CausalMutate = {
      val dm        = current.inner
      val cc        = context
      val nextDot   = cc.max(replicaId).fold(Dot(replicaId, 0))(_.advance)
      val v: DotSet = dm.repr.getOrElse(e, DotSet.empty)

      deltaState[E].make(
        dm = DotMap(Map(e -> DotSet(Dots.single(nextDot)))),
        cc = v.dots add nextDot
      ).mutator
    }

    def addAll(using ReplicaId, PermCausalMutate)(elems: Iterable[E]): C = {
      val dm          = current.inner
      val cc          = context
      val nextCounter = cc.nextTime(replicaId)
      val nextDots    = Dots.from((nextCounter until nextCounter + elems.size).map(Dot(replicaId, _)))

      val ccontextSet = elems.foldLeft(nextDots) {
        case (dots, e) => dm.repr.get(e) match {
            case Some(ds) => dots union ds.dots
            case None     => dots
          }
      }

      deltaState[E].make(
        dm = DotMap((elems zip nextDots.iterator.map(dot => DotSet(Dots.single(dot)))).toMap),
        cc = ccontextSet
      ).mutator
    }

    def remove(using PermQuery, PermCausalMutate)(e: E): C = {
      val dm = current.inner
      val v  = dm.repr.getOrElse(e, DotSet.empty)

      deltaState[E].make(
        cc = v.dots
      ).mutator
    }

    def removeAll(elems: Iterable[E])(using PermQuery, PermCausalMutate): C = {
      val dm = current.inner
      val dotsToRemove = elems.foldLeft(Dots.empty) {
        case (dots, e) => dm.repr.get(e) match {
            case Some(ds) => dots union ds.dots
            case None     => dots
          }
      }

      deltaState[E].make(
        cc = dotsToRemove
      ).mutator
    }

    def removeBy(cond: E => Boolean)(using PermQuery, PermCausalMutate): C = {
      val dm = current.inner
      val removedDots = dm.repr.collect {
        case (k, v) if cond(k) => v
      }.foldLeft(Dots.empty)(_ union _.dots)

      deltaState[E].make(
        cc = removedDots
      ).mutator
    }

    def clear()(using PermQuery, PermCausalMutate): C = {
      val dm = current.inner
      deltaState[E].make(
        cc = dm.dots
      ).mutator
    }

  }

  private class DeltaStateFactory[E] {

    def make(
        dm: DotMap[E, DotSet] = DotMap.empty,
        cc: Dots = Dots.empty
    ): Dotted[AddWinsSet[E]] = Dotted(AddWinsSet(dm), cc)
  }

  private def deltaState[E]: DeltaStateFactory[E] = new DeltaStateFactory[E]

}
