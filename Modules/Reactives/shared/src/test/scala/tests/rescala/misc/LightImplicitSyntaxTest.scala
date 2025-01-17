package tests.rescala.misc

import rescala.core.{CreationTicket, DynamicTicket}
import tests.rescala.testtools.RETests

class LightImplicitSyntaxTest extends RETests {
  multiEngined { engine =>
    import engine._

    test("experiment With Implicit Syntax") {

      implicit def getSignalValueDynamic[T](s: Signal[T])(implicit ticket: DynamicTicket[BundleState]): T =
        ticket.depend(s)
      def Signal[T](f: DynamicTicket[BundleState] => T)(implicit maybe: CreationTicket[BundleState]): Signal[T] =
        engine.Signal.dynamic()(f)

      val price    = Var(3)
      val tax      = price.map { p => p / 3 }
      val quantity = Var(1)
      val total = Signal { implicit t =>
        quantity * (price + tax)
      }

      assert(total.readValueOnce === 4)
      price.set(6)
      assert(total.readValueOnce === 8)
      quantity.set(2)
      assert(total.readValueOnce === 16)

    }

  }
}
