package test.kofre.bespoke

import kofre.base.{Bottom, Lattice}
import kofre.base.Uid.asId
import kofre.datatypes.contextual.{CausalQueue, MultiVersionRegister}
import kofre.datatypes.{GrowOnlyCounter, GrowOnlyList, GrowOnlyMap, GrowOnlySet, LastWriterWins, PosNegCounter}
import kofre.dotted.{Dotted, DottedLattice, HasDots}
import kofre.syntax.ReplicaId
import kofre.time.{Dot, VectorClock}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.*

class DecomposeManualTests extends munit.ScalaCheckSuite {

  val r1: ReplicaId = "r1".asId
  val r2: ReplicaId = "r2".asId

  test("GrowOnlyCounter decomposition") {
    val empty: GrowOnlyCounter = Bottom[GrowOnlyCounter].empty

    val delta_1: GrowOnlyCounter = empty.inc()(using r1)
    assertEquals(delta_1.value, 1)

    // delta_1 and delta_2 are in parallel

    val delta_2: GrowOnlyCounter = empty.inc()(using r2)
    assertEquals(delta_2.value, 1)

    val merged: GrowOnlyCounter = Lattice[GrowOnlyCounter].merge(delta_1, delta_2)
    assertEquals(merged.value, 2)

    val delta_1_diff_delta_2: Option[GrowOnlyCounter] = Lattice[GrowOnlyCounter].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[GrowOnlyCounter] = Lattice[GrowOnlyCounter].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, Some(delta_1), "delta_1 is not contained in delta_2")

    val merged_diff_delta_1: Option[GrowOnlyCounter] = Lattice[GrowOnlyCounter].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[GrowOnlyCounter] = Lattice[GrowOnlyCounter].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[GrowOnlyCounter] = Lattice[GrowOnlyCounter].decompose(merged).toSeq
    // GrowOnlyCounter decomposes into every increment
    assertEquals(decomposed.size, 2)
    assertEquals(decomposed(0).value, 1)
    assertEquals(decomposed(1).value, 1)
  }

  test("PosNegCounter decomposition") {
    val empty: PosNegCounter = Bottom[PosNegCounter].empty

    val delta_1: PosNegCounter = empty.inc()(using r1)
    assertEquals(delta_1.value, 1)

    // delta_1 and delta_2 are in parallel

    val delta_2: PosNegCounter = empty.dec()(using r2)
    assertEquals(delta_2.value, -1)

    val merged: PosNegCounter = Lattice[PosNegCounter].merge(delta_1, delta_2)
    assertEquals(merged.value, 0)

    val delta_1_diff_delta_2: Option[PosNegCounter] = Lattice[PosNegCounter].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[PosNegCounter] = Lattice[PosNegCounter].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, Some(delta_1), "delta_1 is not contained in delta_2")

    val merged_diff_delta_1: Option[PosNegCounter] = Lattice[PosNegCounter].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[PosNegCounter] = Lattice[PosNegCounter].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[PosNegCounter] = Lattice[PosNegCounter].decompose(merged).toSeq.sortBy(_.value)
    // GrowOnlyCounter decomposes into every increment & decrement
    assertEquals(decomposed.size, 2)
    assertEquals(decomposed(0).value, -1)
    assertEquals(decomposed(1).value, 1)
  }

  test("Dotted[MultiVersionRegister[Int]] decomposition") {

    val empty: Dotted[MultiVersionRegister[Int]] = Dotted(Bottom[MultiVersionRegister[Int]].empty)

    val delta_1: Dotted[MultiVersionRegister[Int]] = empty.write(using r1)(1)
    assertEquals(delta_1.read, Set(1))

    // delta_1 and delta_2 are in parallel

    val delta_2: Dotted[MultiVersionRegister[Int]] = empty.write(using r2)(2)
    assertEquals(delta_2.read, Set(2))

    val merged: Dotted[MultiVersionRegister[Int]] = Lattice[Dotted[MultiVersionRegister[Int]]].merge(delta_1, delta_2)
    assertEquals(merged.read, Set(1, 2))

    val delta_1_diff_delta_2: Option[Dotted[MultiVersionRegister[Int]]] = Lattice[Dotted[MultiVersionRegister[Int]]].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[Dotted[MultiVersionRegister[Int]]] = Lattice[Dotted[MultiVersionRegister[Int]]].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, Some(delta_1), "delta_1 is not contained in delta_2")

    val merged_diff_delta_1: Option[Dotted[MultiVersionRegister[Int]]] = Lattice[Dotted[MultiVersionRegister[Int]]].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[Dotted[MultiVersionRegister[Int]]] = Lattice[Dotted[MultiVersionRegister[Int]]].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[Dotted[MultiVersionRegister[Int]]] = Lattice[Dotted[MultiVersionRegister[Int]]].decompose(merged).toSeq.sortBy(_.data.read.headOption)
    // MultiVersionRegister decomposes every version
    assertEquals(decomposed.size, 2)
    assertEquals(decomposed(0).read, Set(1))
    assertEquals(decomposed(1).read, Set(2))
  }

  test("Dotted[LastWriterWins[Int]] decomposition") {
    given bottomInt: Bottom[Int] with {
      override def empty: Int = 0
    }

    val empty: Dotted[LastWriterWins[Int]] = Bottom[Dotted[LastWriterWins[Int]]].empty

    val delta_1: Dotted[LastWriterWins[Int]] = empty.write(1)
    assertEquals(delta_1.read, 1)

    // delta_1 and delta_2 are in parallel

    val delta_2: Dotted[LastWriterWins[Int]] = empty.write(2)
    assertEquals(delta_2.read, 2)

    val merged: Dotted[LastWriterWins[Int]] = Lattice[Dotted[LastWriterWins[Int]]].merge(delta_1, delta_2)
    assertEquals(merged.read, 2)

    val delta_1_diff_delta_2: Option[Dotted[LastWriterWins[Int]]] = Lattice[Dotted[LastWriterWins[Int]]].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[Dotted[LastWriterWins[Int]]] = Lattice[Dotted[LastWriterWins[Int]]].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, None, "delta_1 happened before delta_2 - delta_1 is obsolete")

    val merged_diff_delta_1: Option[Dotted[LastWriterWins[Int]]] = Lattice[Dotted[LastWriterWins[Int]]].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[Dotted[LastWriterWins[Int]]] = Lattice[Dotted[LastWriterWins[Int]]].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[Dotted[LastWriterWins[Int]]] = Lattice[Dotted[LastWriterWins[Int]]].decompose(merged).toSeq
    // LastWriterWins does not decompose, only returns the value.
    // Dotted decomposes context and value, but as LWW is not contextual, context is empty and not decomposed.
    assertEquals(decomposed.size, 1)
    assertEquals(decomposed.head.read, 2)
  }

  test("GrowOnlySet[Int] decomposition") {
    import GrowOnlySet.given

    val empty: GrowOnlySet[Int] = Bottom[GrowOnlySet[Int]].empty

    val delta_1: GrowOnlySet[Int] = empty.insert(1)
    assertEquals(delta_1.elements, Set(1))

    // delta_1 and delta_2 are in parallel

    val delta_2: GrowOnlySet[Int] = empty.insert(2)
    assertEquals(delta_2.elements, Set(2))

    val merged: GrowOnlySet[Int] = Lattice[GrowOnlySet[Int]].merge(delta_1, delta_2)
    assertEquals(merged.elements, Set(1, 2))

    val delta_1_diff_delta_2: Option[GrowOnlySet[Int]] = Lattice[GrowOnlySet[Int]].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[GrowOnlySet[Int]] = Lattice[GrowOnlySet[Int]].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, Some(delta_1), "delta_1 is not contained in delta_2")

    val merged_diff_delta_1: Option[GrowOnlySet[Int]] = Lattice[GrowOnlySet[Int]].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[GrowOnlySet[Int]] = Lattice[GrowOnlySet[Int]].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[GrowOnlySet[Int]] = Lattice[GrowOnlySet[Int]].decompose(merged).toSeq.sortBy(_.elements.headOption)
    // GrowOnlySet decomposes every entry
    assertEquals(decomposed.size, 2)
    assertEquals(decomposed(0).elements, Set(1))
    assertEquals(decomposed(1).elements, Set(2))
  }

  test("Dotted[GrowOnlyMap[Int, LastWriterWins[String]]] decomposition") {
    import GrowOnlyMap.given

    given stringOrdering: Ordering[String] = scala.math.Ordering.String

    given stringLattice: Lattice[String] = Lattice.fromOrdering(stringOrdering)

    given HasDots[String] = HasDots.noDots

    given bottomString: Bottom[String] with {
      override def empty: String = ""
    }

    val emptyMap: Dotted[GrowOnlyMap[Int, LastWriterWins[String]]] = Dotted(Bottom[GrowOnlyMap[Int, LastWriterWins[String]]].empty)

    val k1: Int = 1
    val v1: String = "one"
    val e1 = LastWriterWins.now("")
    val delta_1: Dotted[GrowOnlyMap[Int, LastWriterWins[String]]] = emptyMap.mutateKeyNamedCtx(k1, e1)(_.write(v1))
    assertEquals(delta_1.data.keySet, Set(1))
    assertEquals(delta_1.data.get(1).map(_.payload), Some("one"))

    // delta_1 and delta_2 are in parallel

    val k2: Int = 2
    val v2: String = "two"
    val e2 = LastWriterWins.now("")
    val delta_2: Dotted[GrowOnlyMap[Int, LastWriterWins[String]]] = emptyMap.mutateKeyNamedCtx(k2, e2)(_.write(v2))
    assertEquals(delta_2.data.keySet, Set(2))
    assertEquals(delta_2.data.get(2).map(_.payload), Some("two"))

    val merged: Dotted[GrowOnlyMap[Int, LastWriterWins[String]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].merge(delta_1, delta_2)
    assertEquals(merged.data.keySet, Set(1, 2))
    assertEquals(merged.data.get(1).map(_.payload), Some("one"))
    assertEquals(merged.data.get(2).map(_.payload), Some("two"))

    val delta_1_diff_delta_2: Option[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].diff(delta_1, delta_2)
    assertEquals(delta_1_diff_delta_2, Some(delta_2), "delta_2 is not contained in delta_1")

    val delta_2_diff_delta_1: Option[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].diff(delta_2, delta_1)
    assertEquals(delta_2_diff_delta_1, Some(delta_1), "delta_1 is not contained in delta_2")

    val merged_diff_delta_1: Option[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].diff(merged, delta_1)
    assertEquals(merged_diff_delta_1, None, "delta_1 should be contained in merged")

    val merged_diff_delta_2: Option[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].diff(merged, delta_2)
    assertEquals(merged_diff_delta_2, None, "delta_2 should be contained in merged")

    val decomposed: Seq[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]] = Lattice[Dotted[GrowOnlyMap[Int, LastWriterWins[String]]]].decompose(merged).toSeq.sortBy(_.data.keys.headOption)
    // GrowOnlyMap decomposes every entry.
    // LastWriterWins does not decompose, only returns the value.
    // Dotted decomposes context and value, but as LWW is not contextual, the context is empty and there is no additional entry for it.
    assertEquals(decomposed.size, 2)

    assertEquals(decomposed(0).data.get(1).map(_.payload), Some("one"))
    assertEquals(decomposed(0).context.internal.keySet.size, 0)

    assertEquals(decomposed(1).data.get(2).map(_.payload), Some("two"))
    assertEquals(decomposed(1).context.internal.keySet.size, 0)
  }

}
