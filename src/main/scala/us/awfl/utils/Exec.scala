package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.utils.Events

object Exec {
  // Payload for linking executions
  case class ExecLink(
    callingExec: BaseValue[String],
    triggeredExec: Value[String],
    sessionId: Value[String],
    created: BaseValue[Double]
  )

  // Payload for per-session execution registration
  case class ExecRegistration(
    sessionId: Value[String],
    execId: Value[String],
    created: BaseValue[Double]
  )

  // Status update payload (shared across callers)
  case class StatusUpdate(
    execId: Value[String],
    status: BaseValue[String],
    ended: BaseValue[Boolean],
    workflow: BaseValue[String] = WORKFLOW_ID,
    updated: BaseValue[Double] = Value("sys.now()"),
    error: BaseValue[String] = Value.nil
  )

  // Default collection names
  val linksCollection: Value[String] = str("workflowExecLinks")
  val regsCollection: Value[String]  = str("workflowExecsBySession")

  // Resolve current workflow execution id via generalized placeholder
  def currentExecId: Value[String] = WORKFLOW_EXECUTION_ID

  // Register caller -> triggered linkage (idempotent: create, on 409 update)
  // Returns a Step that yields "ok" | "skip" | "fail"
  def registerExecLink(
    name: String,
    callingExecId: BaseValue[String],
    triggeredExecId: Value[String],
    collection: Value[String] = linksCollection
  ) = {
    val execPayload = obj(ExecLink(
      callingExec = callingExecId,
      triggeredExec = triggeredExecId,
      sessionId = Env.sessionId,
      created = Value("sys.now()")
    ))

    // Use a composite id per child to avoid overwrites when one caller triggers multiple children
    val docId: Value[String] = str(callingExecId.cel + (":": Cel) + triggeredExecId.cel)

    val createExecLink = us.awfl.services.Firebase.create(
      s"${name}Create",
      collection,
      id = docId,
      contents = execPayload
    )

    val updateExecLink = us.awfl.services.Firebase.update(
      s"${name}Update",
      collection,
      id = docId,
      contents = execPayload
    )

    // Only attempt to write if caller exec id is non-empty
    Switch(
      name,
      List(
        (CelFunc("len", CelFunc("default", callingExecId.cel, "")) > 0) -> {
          Try(
            s"${name}Try",
            List(createExecLink) -> str("ok"),
            err => Switch(
              s"${name}Switch",
              List(
                (err.get.code.cel === 409) -> (List(updateExecLink) -> str("ok")),
                (true: Cel) -> (List(Raise(s"${name}Rethrow", err)) -> str("fail"))
              )
            ).fn
          ).fn
        },
        (true: Cel) -> (List() -> str("skip"))
      )
    )
  }

  // Register this execution under the session (always). Idempotent via create then update on 409.
  def registerExecForSession(
    name: String,
    execId: Value[String],
    collection: Value[String] = regsCollection
  ) = {
    val docId: Value[String] = str(Env.sessionId.cel + (":": Cel) + execId.cel)
    val payload = obj(ExecRegistration(
      sessionId = Env.sessionId,
      execId = execId,
      created = Value("sys.now()")
    ))

    val create = us.awfl.services.Firebase.create(s"${name}Create", collection, id = docId, contents = payload)
    val update = us.awfl.services.Firebase.update(s"${name}Update", collection, id = docId, contents = payload)

    Try(
      s"${name}Try",
      List(create) -> str("ok"),
      err => Switch(
        s"${name}Switch",
        List(
          (("code" in err) && (err.get.code.cel === 409)) -> (List(update) -> str("ok")),
          (true: Cel) -> (List(Raise(s"${name}Rethrow", err)) -> str("fail"))
        )
      ).fn
    )
  }

  // Update execution status via server route (best-effort caller can wrap in Try)
  def updateExecStatus(
    name: String,
    execId: Value[String],
    status: BaseValue[String],
    ended: BaseValue[Boolean],
    error: BaseValue[String] = Value.nil,
    workflow: BaseValue[String] = WORKFLOW_ID
  ) = {
    us.awfl.utils.post[StatusUpdate, NoValueT](
      name,
      "workflows/exec/status/update",
      obj(StatusUpdate(execId, status, ended, workflow, Value("sys.now()"), error))
    )
  }

  // Enqueue a status update notification to Pub/Sub for out-of-band consumers
  def enqueueExecStatus(
    name: String,
    status: BaseValue[String],
    error: BaseValue[String] = Value.nil
  ) = {
    Events.enqueueResponse(
      s"${name}",
      callback_id = Value.nil,
      content = Value.nil,
      toolCall = Value.nil,
      cost = Value(0.0),
      status = status,
      error = error
    )
  }
}
