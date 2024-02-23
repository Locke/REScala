package rescala.extra

import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.{Element, KeyboardEvent, MutationObserver, Node, Range, document}
import rescala.core.{CreationTicket, Disconnectable, DynamicScope, Tracing}
import rescala.operator.Interface
import rescala.structure.RExceptions.ObservedException
import rescala.structure.{Observe, Pulse}

import scala.annotation.targetName
import scala.scalajs.js

object Tags extends Tags[rescala.default.type](rescala.default, true)

class Tags[Api <: Interface](val api: Api, val addDebuggingIds: Boolean) {
  import api.*

  trait RangeSplice[-T]:
    def splice(range: dom.Range, value: T): Unit
  object RangeSplice:
    given elem: RangeSplice[dom.Element] with {
      override def splice(range: Range, value: dom.Element) =
        range.insertNode(value)
    }
    given many[T](using other: RangeSplice[T]): RangeSplice[Seq[T]] with {
      override def splice(range: Range, value: Seq[T]) =
        value.reverseIterator.foreach(v => other.splice(range, v))
    }
    given string: RangeSplice[String] with {
      override def splice(range: Range, value: String) =
        range.insertNode(document.createTextNode(value))
    }

  extension (outer: dom.Element)
    def reattach[T](signal: Signal[T])(using splicer: RangeSplice[T]): outer.type = {
      val range = document.createRange()
      range.selectNodeContents(outer)
      range.collapse(toStart = false)
      Observe.strong(signal, true) {
        tagObserver(outer, signal) { v =>
          range.deleteContents()
          splicer.splice(range, v)
        }
      }
      outer
    }

  extension (input: Input)
    def inputEntered: Event[String] = {
      val handler: Event.CBR[KeyboardEvent, Unit] = Event.fromCallback(input.onkeyup = Event.handle(_))

      handler.event
        .map { (e: KeyboardEvent) =>
          if e.key == "Enter" then
            val res = input.value.trim
            if res.nonEmpty then
              e.preventDefault()
              input.value = ""
              Some(res)
            else None
          else None
        }.flatten
    }

  def isInDocument(element: Element): Boolean = {
    js.Dynamic.global.document.contains(element).asInstanceOf[Boolean]
  }

  /* This only returns true the second time it is called to prevent observers to directly trigger */
  def isInDocumentHack(elem: dom.Element): Any => Boolean = {
    var second = false
    _ => {
      if (second) {
        !isInDocument(elem)
      } else {
        second = true
        false
      }
    }
  }

  def tagObserver[A](
      parent: dom.Element,
      rendered: Signal[A]
  )(fun: A => Unit)(reevalVal: Pulse[A]): Observe.ObserveInteract =
    new Observe.ObserveInteract {
      override def checkExceptionAndRemoval(): Boolean = {
        reevalVal match {
          case Pulse.empty | Pulse.NoChange => false
          case Pulse.Exceptional(f) =>
            throw ObservedException(rendered, s"signal tag attached to $parent observed", f)
          case Pulse.Value(v) =>
            isInDocumentHack(parent)(v)
        }
      }

      override def execute(): Unit =
        reevalVal match {
          case Pulse.empty | Pulse.NoChange => ()
          case Pulse.Value(v) =>
            fun(v)
          case Pulse.Exceptional(f) =>
            throw new IllegalStateException("should have aborted earlier", f)
        }
    }

}
