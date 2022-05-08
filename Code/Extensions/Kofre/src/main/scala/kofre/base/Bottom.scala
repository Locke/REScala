package kofre.base

import kofre.contextual.ContextDecompose

/** Bottom.empty is the identity of Lattice.merge */
trait Bottom[A] {
  def empty: A
}
object Bottom {
  def empty[A](using bottom: Bottom[A]): A = bottom.empty
  def apply[A](using bottom: Bottom[A]): Bottom[A] = bottom

  given decomposeBottom[A](using dl: DecomposeLattice[A]): Bottom[A] = dl
}
