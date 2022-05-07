package test.kofre

import kofre.causality.{CausalContext, Dot}
import kofre.dotbased.DotStore.*
import kofre.dotbased.{WithContext, DotStore}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import test.kofre.DataGenerator.*
import kofre.Lattice.Operators
import kofre.Lattice

class DotSetTests extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {

  "Manual Tests" in {
    val d1 = Dot("1", 1)
    val d2 = Dot("2", 1)

    val s1 = Set(d1)
    assert(DotSetInstance.dots(s1).contains(d1))

    val s2 = Set(d2)
    val c1 = Set(d1)
    val c2 = Set(d1, d2) // d1 is already deleted in the second causal context

    val mergedStore = Lattice.merge(
      WithContext(s1, CausalContext.fromSet(c1)),
      WithContext(s2, CausalContext.fromSet(c2))
    ).store

    assert(!mergedStore.contains(d1))
    assert(mergedStore.contains(d2))
  }

  "merge" in forAll { (s1: Set[Dot], t1: Set[Dot], s2: Set[Dot], t2: Set[Dot]) =>
    // add all current values to their causal contexts
    val c1 = s1 ++ t1
    val c2 = s2 ++ t2

    // commutativity
    val m1 = Lattice.merge(WithContext(s1, CausalContext.fromSet(c1)), WithContext(s2, CausalContext.fromSet(c2)))
    val m2 = Lattice.merge(WithContext(s2, CausalContext.fromSet(c2)), WithContext(s1, CausalContext.fromSet(c1)))
    assert(m1 == m2)

    // check if all elements were added to the new causal context
    for (e <- c1) yield {
      assert(m1.context.contains(e))
    }
    for (e <- c2) yield {
      assert(m1.context.contains(e))
    }

    val deadElements = c1.filter(!s1.contains(_)) ++ c2.filter(!s2.contains(_))
    val newElements  = (s1 union s2) -- deadElements

    // check that already deleted elements are not added again
    for (e <- deadElements) yield {
      assert(!m1.store.contains(e))
    }

    // check that the new store contains all new elements
    for (e <- newElements) yield {
      assert(m1.store.contains(e))
    }
  }
}
