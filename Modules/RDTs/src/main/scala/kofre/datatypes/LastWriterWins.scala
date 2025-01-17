package kofre.datatypes

import kofre.base.{Bottom, Lattice, Time, Uid}
import kofre.datatypes.MultiVersionRegister
import kofre.dotted.{DotFun, Dotted, DottedLattice, HasDots}
import kofre.syntax.{OpsSyntaxHelper, ReplicaId}
import kofre.time.{Dot, Dots}

import scala.math.Ordering.Implicits.infixOrderingOps

/** A LastWriterWins (register) is a common fallback for datatypes that don’t have good merge semantics.
  *
  * This implementation uses [[Dot]]s to have “last” coincide with causal order (independent of wall clock time).
  * Concurrent writes are resolved by wallclock time first (favouring replicas with clock drift into the future),
  * and by replica ID, in case both writes happened in the same millisecond.
  */
case class LastWriterWins[+A](dot: Dot, wallTime: Time, payload: A)

object LastWriterWins {

  def empty[A: Bottom](dot: Dot): LastWriterWins[A] = now(dot, Bottom.empty)

  def fallback[A](dot: Dot, v: A): LastWriterWins[A] = LastWriterWins(dot, Long.MinValue, v)

  def now[A](dot: Dot, v: A): LastWriterWins[A] = LastWriterWins(dot, Time.current(), v)

  given [A]: HasDots[LastWriterWins[A]] with {
    extension (value: LastWriterWins[A])
      def dots: Dots = Dots.single(value.dot)
      def removeDots(dots: Dots): Option[LastWriterWins[A]] =
        if dots.contains(value.dot) then None else Some(value)
  }

  given ordering: Ordering[LastWriterWins[Any]] =
    Ordering.by[LastWriterWins[Any], Time](_.wallTime)
      .orElseBy(_.dot.replicaId)
      .orElseBy(_.dot.time)
      .orElse(MultiVersionRegister.assertEqualsOrdering.on(_.payload))

  given dottedLattice[A]: DottedLattice[LastWriterWins[A]] with {
    override def mergePartial(left: Dotted[LastWriterWins[A]], right: Dotted[LastWriterWins[A]]): LastWriterWins[A] =
      if left.context.contains(right.data.dot)
      then left.data
      else if right.context.contains(left.data.dot)
      then right.data
      else if ordering.lteq(left.data, right.data)
      then right.data
      else left.data
  }

  extension [C, A](container: C)
    def causalLastWriterWins: syntax[C, A] = syntax(container)

  implicit class syntax[C, A](container: C)
      extends OpsSyntaxHelper[C, LastWriterWins[A]](container) {

    def read(using PermQuery): A = current.payload

    def write(using ReplicaId)(v: A): CausalMutate =
      val nextDot = context.nextDot(replicaId)
      Dotted(
        LastWriterWins.now(nextDot, v),
        Dots.single(current.dot).add(nextDot)
      ).mutator

    def map[B](using ReplicaId, PermCausalMutate)(using ev: A =:= Option[B])(f: B => B): C =
      read.map(f) match {
        case None => container
        case res  => write(ev.flip(res))
      }
  }
}
