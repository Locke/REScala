package todo

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import kofre.decompose.Delta
import loci.registry.Binding
import loci.serializer.jsoniterScala._
import org.scalajs.dom.html.{Div, Input, LI}
import org.scalajs.dom.{UIEvent, window}
import rescala.default.Events.CBResult
import rescala.default._
import rescala.extra.Tags._
import rescala.extra.replication.LociDist
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.tags2.section
import scalatags.JsDom.{Attr, TypedTag}
import todo.Codecs._
import todo.Todolist.replicaId
import kofre.decompose.interfaces.LWWRegisterInterface.LWWRegisterSyntax
import kofre.decompose.interfaces.RGAInterface.{RGA, RGASyntax}
import rescala.extra.replication.containers.ReactiveDeltaCRDT

class TodoAppUI(val storagePrefix: String) {

  implicit val stringCodec: JsonValueCodec[String] = JsonCodecMaker.make

  @scala.annotation.nowarn // Auto-application to `()`
  def getContents(): TypedTag[Div] = {

    val todoInputTag: JsDom.TypedTag[Input] = input(
      id          := "newtodo",
      `class`     := "new-todo",
      placeholder := "What needs to be done?",
      autofocus   := "autofocus"
    )

    val createTodo = inputFieldHandler(todoInputTag, onchange)

    val removeAll = Events.fromCallback[UIEvent](cb => button("remove all done todos", onclick := cb))

    val toggleAll = Events.fromCallback[UIEvent] { cb =>
      input(id := "toggle-all", name := "toggle-all", `class` := "toggle-all", `type` := "checkbox", onchange := cb)
    }

    val taskrefs = TaskReferences(toggleAll.event, storagePrefix)
    val taskOps  = new TaskOps(taskrefs)

    val deltaEvt = Evt[Delta[RGA[TaskRef]]]

    val tasksRDT: Signal[ReactiveDeltaCRDT[RGA[TaskRef]]] =
      Storing.storedAs(storagePrefix, ReactiveDeltaCRDT[RGA[TaskRef]](replicaId)) { init =>
        Events.foldAll(init) { current =>
          Seq(
            createTodo.event act taskOps.handleCreateTodo(current),
            removeAll.event dyn { dt => _ => taskOps.handleRemoveAll(current, dt) },
            new RGASyntax(current).toList.map(_.removed) act taskOps.handleRemove(current),
            deltaEvt act taskOps.handleDelta(current)
          )
        }
      }(codecRGA)

    LociDist.distributeDeltaCRDT(tasksRDT, deltaEvt, Todolist.registry)(
      Binding[RGA[TaskRef] => Unit]("tasklist")
    )

    val tasksList: Signal[List[TaskRef]] = tasksRDT.map { _.toList }
    val tasksData: Signal[List[TaskData]] =
      Signal.dynamic { tasksList.value.flatMap(l => new LWWRegisterSyntax(l.task.value).read) }
    val taskTags: Signal[List[TypedTag[LI]]] = Signal { tasksList.value.map(_.tag) }

    val largeheader = window.location.hash.substring(1)

    div(
      `class` := "todoapp",
      header(
        `class` := "header",
        h1(if (largeheader.nonEmpty) largeheader else "todos"),
        createTodo.data
      ),
      section(
        `class` := "main",
        `style` := Signal { if (tasksData.value.isEmpty) "display:hidden" else "" },
        toggleAll.data,
        label(`for` := "toggle-all", "Mark all as complete"),
        ul(
          `class` := "todo-list",
          taskTags.asModifierL
        )
      ),
      div(
        `class` := "footer",
        `style` := Signal { if (tasksData.value.isEmpty) "display:none" else "" },
        Signal {
          val remainingTasks = tasksData.value.count(!_.done)
          span(
            `class` := "todo-count",
            strong("" + remainingTasks),
            span(if (remainingTasks == 1)
              " item left"
            else " items left")
          )
        }.asModifier,
        Signal {
          removeAll.data(`class` := s"clear-completed${if (!tasksData.value.exists(_.done)) " hidden" else ""}")
        }.asModifier
      )
    )
  }

  def inputFieldHandler(tag: TypedTag[Input], attr: Attr): CBResult[String, Input] = {
    val handler = Events.fromCallback[UIEvent](cb => tag(attr := cb))

    val todoInputField: Input = handler.data.render

    val handlerEvent =
      handler.event.map { e: UIEvent =>
        e.preventDefault()
        val res = todoInputField.value.trim
        todoInputField.value = ""
        res
      }

    new CBResult(handlerEvent, todoInputField)
  }

}
