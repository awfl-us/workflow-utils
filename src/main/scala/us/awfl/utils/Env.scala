package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.auto.given

case class Env(
  userId: Value[String] = Env.userId,
  model: Value[String] = Env.model,
  // Caller exec id (if this workflow was triggered by another workflow)
  callingWorkflowExec: OptValue[String] = OptValue(Exec.currentExecId),
  BASE_URL: Value[String] = Env.BASE_URL,
  background: OptValue[Boolean] = Env.background,
  projectId: Value[String] = Env.projectId,
  sessionId: Value[String] = Env.sessionId
)
val ENV: BaseValue[Env] = obj(Env())
object Env {
  given get: Env = Value[Env]("input.env").get
  export get.*
}