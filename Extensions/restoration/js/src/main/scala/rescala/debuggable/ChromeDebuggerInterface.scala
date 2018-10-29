package rescala.debuggable

import rescala.core.{InitialChange, Pulse, ReSource}
import rescala.restoration.{ReStoreImpl, ReStoringStruct}

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSON

object ChromeDebuggerInterface extends DebuggerInterface {

//  val map = mutable.Map[String, ]()

  def saveNode(id: NodeID, name: String, value: String): Unit = {
    val msg = literal(
      destination = "panel",
      action = "save",
      content = literal(
        nodeId = id.str,
        nodeRef = name,
        nodeValue = value,
        action = "node",
//      nodeType: type,
//      nodeMethod: method,
//      sourceInfo = sourceInfo,
        ),
    )
    send(msg)
  }

  def saveEdge(from: NodeID, to: NodeID): Unit = {
    val msg = literal(
      destination = "panel",
      action = "save",
      content = literal(
        edgeStart = from.str,
        edgeEnd = to.str,
        action = "edge",
//      edgeStartName: edgeStart ? edgeStart.name : '',
//      edgeEndName: edgeEnd ? edgeEnd.name : '',
//      edgeLabel: name
        ),
    )
    send(msg)
  }


  override def saveSnap(snapshotid: String): Unit = {
    val msg = literal(
      destination = "panel",
      action = "save",
      content = literal(
        snapshotid = snapshotid,
        action = "snap",
      ),
    )
    send(msg)
  }


  override def sourceHint(id    : NodeID,
                          hint  : String,
                          values: Seq[String]): Unit = {
    val msg = literal(
      destination = "panel",
      action = "sourceHint",
      content = literal(
        nodeId = id.str,
        hint = hint,
        values = js.Array(values)
      )
    )
    send(msg)
  }


  def send(data: js.Object) = {
      org.scalajs.dom.window.postMessage(data, "*")
  }

  def setup(reStore: ReStoreImpl): Unit = {
    org.scalajs.dom.window.onmessage = {e: org.scalajs.dom.MessageEvent =>
      val data = e.data.asInstanceOf[js.Dynamic]
      if (data.destination.asInstanceOf[String] == "rescala") {
        data.`type`.asInstanceOf[String] match {
          case "set-signal" => {
            val nodeId = data.nodeId.asInstanceOf[String]
            val value = data.value.asInstanceOf[String]
            // TODO set nodeId to value
            val r: ReSource[ReStoringStruct] = reStore.registeredNodes(nodeId)
            reStore.executeTurn(r){ at =>
              at.recordChange(new InitialChange[ReStoringStruct] {
                override val source: ReSource[ReStoringStruct] = r
                override def writeValue(b: source.Value, v: source.Value => Unit): Boolean = {v(Pulse.Value(value).asInstanceOf[source.Value]); true}
              })
            }
            println(JSON.stringify(nodeId) + " fire " + JSON.stringify(value))
          }

          case "timeTravel" =>
            val snapId = data.snapId.toString
            reStore.restoreSnap(snapId)
        }
      }
    }
  }

}
