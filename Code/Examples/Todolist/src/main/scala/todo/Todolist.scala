package todo

import loci.registry.Registry
import org.scalajs.dom.{document, window}
import kofre.base.Id

import java.util.concurrent.ThreadLocalRandom
import scala.scalajs.js.annotation.JSExportTopLevel

object Todolist {

  val replicaId: Id = Id.gen()

  val registry = new Registry

  @JSExportTopLevel("Todolist")
  def run(): Unit = main(Array.empty[String])

  def main(args: Array[String]): Unit = {

    val storagePrefix = window.location.href
    println(storagePrefix)

    val todoApp = new TodoAppUI(storagePrefix)
    val div     = todoApp.getContents()

    val webrtc = WebRTCHandling(registry)

    document.body.replaceChild(div.render, document.body.firstElementChild)
    document.body.appendChild(webrtc.webrtcHandlingArea.render)
    ()
  }

}
