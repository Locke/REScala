import rescala.operator.Interface
import rescala.core.Scheduler
import rescala.core.{AdmissionTicket, ReSource, Scheduler}
import rescala.operator.Interface
import rescala.scheduler.{Levelbased, LevelbasedVariants, Sidup, SynchronizedSidup, TopbundleImpl, TopoBundle}

/** see [[rescala.default]] */
package object rescala {

  /** REScala has two main abstractions. [[rescala.default.Event]] and [[rescala.default.Signal]] commonly referred to as reactives.
    * Use [[rescala.default.Var]] to create signal sources and [[rescala.default.Evt]] to create event sources.
    *
    * Events and signals can be created from other reactives by using combinators,
    * signals additionally can be created using [[rescala.default.Signal]] expressions.
    */
  object default extends Interface.FromScheduler(rescala.interfaces.defaultPlatformScheduler)
}
