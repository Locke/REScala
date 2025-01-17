package kofre.dotted

import kofre.base.{Bottom, Uid, Lattice}
import kofre.dotted.{DotFun, DotSet}
import kofre.syntax.{PermCausalMutate, PermQuery}
import kofre.time.{Dot, Dots}

/** Associates a context of Dots with some data structure.
  * The most common use is to interpret the context as the set of:
  * • all dots that are present in data
  * • all dots directly subsumed by data
  *
  * Specifically, the `deletions` and `contained` methods reflect this interpretation.
  * */
case class Dotted[A](data: A, context: Dots) {
  def map[B](f: A => B): Dotted[B]      = Dotted(f(data), context)
  def knows(dot: Dot): Boolean          = context.contains(dot)
  def deletions(using HasDots[A]): Dots = context diff contained
  def contained(using HasDots[A]): Dots = data.dots
}

object Dotted {

  def empty[A: Bottom]: Dotted[A] = Dotted(Bottom.empty[A], Dots.empty)
  def apply[A](a: A): Dotted[A]   = Dotted(a, Dots.empty)

  // causes DottedLattice instance to be found in some cases where we are only looking for a Lattice[Dotted[A]]
  export DottedLattice.given

  given syntaxPermissions[L](using DottedLattice[L]): PermCausalMutate[Dotted[L], L] with {
    override def mutateContext(c: Dotted[L], delta: Dotted[L]): Dotted[L] = c merge delta
    override def query(c: Dotted[L]): L                                   = c.data
    override def context(c: Dotted[L]): Dots                              = c.context
  }

}
