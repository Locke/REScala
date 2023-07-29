package kofre.datatypes.alternatives.lww

import kofre.base.Uid

/** Last-writer-wins implementation using only [[WallClock]] time */
case class TimedVal[+A](timestamp: WallClock, payload: A) extends ILastWriterWins[WallClock, A]

object TimedVal:
  def now[Value](payload: Value, replicaId: Uid): TimedVal[Value] =
    TimedVal(WallClock.now(replicaId), payload)
