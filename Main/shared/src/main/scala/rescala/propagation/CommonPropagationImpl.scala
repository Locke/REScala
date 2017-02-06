package rescala.propagation

import java.util

import rescala.graph.{DepDiff, PulsingGraphStruct, EvaluationSpore, Reactive}

import scala.util.control.NonFatal

/**
  * Basic implementation of the most fundamental propagation steps as defined by AbstractPropagation.
  * Only compatible with spore definitions that store a pulse value and support graph operations.
  *
  * @tparam S Struct type that defines the spore type used to manage the reactive evaluation
  */
trait CommonPropagationImpl[S <: PulsingGraphStruct] extends AbstractPropagation[S] {
  private val toCommit = new java.util.ArrayList[Committable]()
  private val observers = new java.util.ArrayList[() => Unit]()
  override def schedule(commitable: Committable): Unit = {
    assert(!toCommit.contains(commitable), s"tried to schedule same commitable twice: $commitable")
    toCommit.add(commitable)
  }

  override def observe(f: => Unit): Unit = observers.add(f _)


  override def commitPhase(): Unit = {
    val it = toCommit.iterator()
    while (it.hasNext) it.next().commit(this)
  }

  override def rollbackPhase(): Unit = {
    val it = toCommit.iterator()
    while (it.hasNext) it.next().release(this)
  }

  override def observerPhase(): Unit = {
    val it = observers.iterator()
    var failure: Throwable = null
    while (it.hasNext) {
      try {
        it.next().apply()
      }
      catch {
        case NonFatal(e) => failure = e
      }
    }
    // find the first failure and rethrow the contained exception
    // we should probably aggregate all of the exceptions,
    // but this is not the place to invent exception aggregation
    if (failure != null) throw failure
  }

  protected def discover(sink: Reactive[S])(source: Reactive[S]): Unit = source.bud.discover(sink)(this)

  protected def drop(sink: Reactive[S])(source: Reactive[S]): Unit = source.bud.drop(sink)(this)

  final def applyDiff(head: Reactive[S], diff: DepDiff[S]): Unit = {
    head.bud.updateIncoming(diff.novel)(this)
    diff.removed foreach drop(head)
    diff.added foreach discover(head)
  }



}
