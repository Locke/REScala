package kofre.time

import kofre.base.{Lattice, Uid}
import kofre.dotted.Dotted

/** Essentially a more efficient version of a [[Set[Dot] ]].
  * It typically tracks all dots known within some scope.
  *
  * This datastructure is used both for implementation of RDTs,
  * as well as for ensuring causality during replication.
  */
case class Dots(internal: Map[Uid, ArrayRanges]) {

  def wrap[A](a: A): Dotted[A] = Dotted(a, this)

  def isEmpty: Boolean = internal.forall((_, r) => r.isEmpty)

  def rangeAt(replicaId: Uid): ArrayRanges = internal.getOrElse(replicaId, ArrayRanges.empty)

  def clockOf(replicaId: Uid): Option[Dot] = max(replicaId)

  def clock: VectorClock = VectorClock(internal.view.mapValues(_.next.fold(0L)(_ - 1)).toMap)

  def add(dot: Dot): Dots = add(dot.replicaId, dot.time)

  def add(replicaId: Uid, time: Time): Dots =
    Dots(internal.updated(
      replicaId,
      rangeAt(replicaId).add(time)
    ))

  def nextTime(replicaId: Uid): Time = rangeAt(replicaId).next.getOrElse(0)

  def nextDot(replicaId: Uid): Dot = Dot(replicaId, nextTime(replicaId))

  def advanced(replicaId: Uid): Dots = {
    val next = this.nextDot(replicaId)
    this.add(next)
  }

  def diff(other: Dots): Dots = subtract(other)

  def subtract(other: Dots): Dots = {
    Dots(
      internal.map { case left @ (id, leftRanges) =>
        other.internal.get(id) match {
          case Some(rightRanges) => id -> (leftRanges subtract rightRanges)
          case None              => left
        }
      }.filterNot(_._2.isEmpty)
    )
  }

  def intersect(other: Dots): Dots =
    Dots {
      internal.flatMap { case (id, ranges) =>
        other.internal.get(id) match {
          case Some(otherRanges) =>
            val intersection = ranges intersect otherRanges
            if (intersection.isEmpty) None
            else Some(id -> intersection)
          case None => None
        }
      }
    }

  def disjunct(other: Dots): Boolean =
    val keys = internal.keySet intersect other.internal.keySet
    keys.forall { k =>
      rangeAt(k) disjunct other.rangeAt(k)
    }

  def union(other: Dots): Dots = Dots.contextLattice.merge(this, other)

  def contains(d: Dot): Boolean = internal.get(d.replicaId).exists(_.contains(d.time))

  def contains(other: Dots): Boolean = other <= this

  def iterator: Iterator[Dot] = internal.iterator.flatMap((k, v) => v.iterator.map(t => Dot(k, t)))

  def toSet: Set[Dot] =
    internal.flatMap((key, tree) => tree.iterator.map(time => Dot(key, time))).toSet

  def max(replicaID: Uid): Option[Dot] =
    internal.get(replicaID).flatMap(_.next.map(c => Dot(replicaID, c - 1)))

  def <=(other: Dots): Boolean = internal.forall {
    case (id, leftRange) => leftRange <= other.rangeAt(id)
  }
}

object Dots {
  def single(replicaId: Uid, time: Long): Dots = empty.add(replicaId, time)

  val empty: Dots = Dots(Map.empty)

  def single(dot: Dot): Dots = empty.add(dot.replicaId, dot.time)

  given contextLattice: Lattice[Dots] = Lattice.derived

  def from(dots: Iterable[Dot]): Dots = Dots(dots.groupBy(_.replicaId).view.mapValues {
    times => ArrayRanges.from(times.view.map(_.time))
  }.toMap)

  given partialOrder: PartialOrdering[Dots] with {
    override def tryCompare(x: Dots, y: Dots): Option[Int] =
      var leftLTE  = true
      var rightLTE = true
      (x.internal.keySet concat y.internal.keySet).forall { k =>
        ArrayRanges.partialOrder.tryCompare(x.rangeAt(k), y.rangeAt(k)) match
          case None =>
            leftLTE = false
            rightLTE = false
          case Some(-1) =>
            rightLTE = false
          case Some(1) =>
            leftLTE = false
          case Some(0) =>
          case Some(_) => throw IllegalStateException("does not happen")
        end match
        leftLTE || rightLTE
      }
      ArrayRanges.leftRightToOrder(leftLTE, rightLTE)

    override def lteq(x: Dots, y: Dots): Boolean = x <= y
  }

}
