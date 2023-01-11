package kofre.datatypes

import kofre.base.{Bottom, DecomposeLattice, Id, Time}
import kofre.dotted.DottedDecompose
import kofre.syntax.PermIdMutate.withID
import kofre.syntax.{OpsSyntaxHelper, OpsTypes, PermMutate, PermQuery}

case class Epoche[E](counter: Time, value: E)

object Epoche {

  def empty[E: Bottom]: Epoche[E] = Epoche(0, Bottom[E].empty)

  given contextDecompose[E: DecomposeLattice]: DottedDecompose[Epoche[E]] = DottedDecompose.liftDecomposeLattice

  given bottom[E: Bottom]: Bottom[Epoche[E]] with { override def empty: Epoche[E] = Epoche.empty }

  extension[C, E] (container: C)
    def epoche: syntax[C, E] = syntax(container)

  implicit class syntax[C, E](container: C)
      extends OpsSyntaxHelper[C, Epoche[E]](container) {
    def read(using QueryP): E = current.value

    def write(using MutationP)(value: E): C       = current.copy(value = value).mutator
    def epocheWrite(using MutationP)(value: E): C = Epoche(current.counter + 1, value).mutator

    def map(using MutationP)(f: E => E): C = write(f(current.value))
  }

  given epocheAsUIJDLattice[E: DecomposeLattice]: DecomposeLattice[Epoche[E]] = new DecomposeLattice[Epoche[E]] {

    override def lteq(left: Epoche[E], right: Epoche[E]): Boolean = (left, right) match {
      case (Epoche(cLeft, vLeft), Epoche(cRight, vRight)) =>
        cLeft < cRight || (cLeft == cRight && DecomposeLattice[E].lteq(vLeft, vRight))
    }

    /** Decomposes a lattice state into ic unique irredundant join decomposition of join-irreducible states */
    override def decompose(state: Epoche[E]): Iterable[Epoche[E]] = state match {
      case Epoche(c, v) =>
        DecomposeLattice[E].decompose(v).map(Epoche(c, _))
    }

    /** By assumption: associative, commutative, idempotent. */
    override def merge(left: Epoche[E], right: Epoche[E]): Epoche[E] = (left, right) match {
      case (Epoche(cLeft, vLeft), Epoche(cRight, vRight)) =>
        if (cLeft > cRight) left
        else if (cRight > cLeft) right
        else Epoche(cLeft, DecomposeLattice[E].merge(vLeft, vRight))
    }
  }
}
