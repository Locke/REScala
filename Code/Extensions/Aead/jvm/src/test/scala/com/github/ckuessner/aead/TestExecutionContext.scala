package com.github.ckuessner.aead

import scala.concurrent.ExecutionContext

object TestExecutionContext {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
}
