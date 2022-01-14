
package todolist


import javafx.scene.control.ListCell
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty
import scalafx.beans.value.ObservableValue
import scalafx.event.subscriptions.Subscription
import scalafx.geometry.Pos
import scalafx.scene.control.{CheckBox, TextField}
import scalafx.scene.layout.{HBox, Priority}

import java.util.UUID

class TodoItemListCell extends ListCell[UUID] {
  override def updateItem(uuid: UUID, empty: Boolean): Unit = {
    super.updateItem(uuid, empty)
    reset()
    if (empty || uuid == null) {
      setText(null)
      setGraphic(null)
    } else {
      val todoProperty: Option[ObjectProperty[TodoEntry]] = TodoListController.getTodo(uuid)

      if (todoProperty.isEmpty) {
        Console.println("Empty")
        return
      }

      val textField = new TextField {
        hgrow = Priority.Always
      }
      textField.setText(todoProperty.get.value.description)

      val checkBox = new CheckBox()
      checkBox.setSelected(todoProperty.get.value.completed)

      val rootContainer = new HBox {
        children = List(checkBox, textField)
        alignment = Pos.CenterLeft
      }
      setGraphic(rootContainer)

      subscriptions :+ textField.text.onChange { (source: ObservableValue[String, String], oldVal, newVal) =>
        val uuid = getItem
        if (uuid != null) todoProperty match {
          case Some(property) => TodoListController.changeTodo(uuid, property.value.copy(description = newVal))
          case None => Console.err.println(s"TodoItemListCell: Entry $uuid not present in Controller")
        }
      }

      subscriptions :+ checkBox.selectedProperty.onChange {
        (observable: ObservableValue[Boolean, java.lang.Boolean], oldVal, newVal) =>
          val uuid = getItem
          if (uuid != null) todoProperty match {
            case Some(property) => TodoListController.changeTodo(uuid, property.value.copy(completed = newVal))
            case None => Console.err.println(s"TodoItemListCell: Entry $uuid not present in Controller")
          }
      }

      subscriptions :+ todoProperty.get.onChange(
        (observable: ObservableValue[TodoEntry, TodoEntry], before: TodoEntry, after: TodoEntry) => Platform.runLater {
          if (textField.getText == before.description && before.description != after.description) {
            textField.setText(after.description)
          }
          if (checkBox.selected.get() == before.completed && before.completed != after.completed) {
            checkBox.setSelected(after.completed)
          }
        })
    }
  }

  private var subscriptions = List.empty[Subscription]

  private def reset(): Unit = {
    Console.println(s"Reset $subscriptions")
    subscriptions.foreach(sub => sub.cancel())
    subscriptions = List.empty
  }
}
