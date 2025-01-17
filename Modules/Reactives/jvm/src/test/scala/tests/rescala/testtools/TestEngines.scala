package tests.rescala.testtools

import rescala.interfaces.*
import rescala.fullmv.FullMVApi
import rescala.operator.Interface

import scala.concurrent.duration.DurationInt

object TestEngines {
  val fullMV = new FullMVApi(10.milliseconds, "generic FullMV Test API")

  val all: List[Interface] = List(synchron, parrp, toposort /*, fullMV */, sidup)
}
