package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ToolCall
import us.awfl.utils.PostRequest

object Events {
  // Event payload delivered to CLI via awfl-relay (data field)
  case class OperationEnvelope(
    create_time: Value[String],
    callback_id: Value[String],
    content: Value[String],
    tool_call: BaseValue[ToolCall],
    cost: BaseValue[Double],
    background: BaseValue[Boolean] = Env.background.getOrElse(Value(false)),
    workdir: Value[String] = Value.nil,
    // Optional status update fields (backward-compatible defaults)
    status: Value[String] = str(""),
    error: Value[String] = Value.nil
  )

  // Relay ingest body wrapper
  case class RelayEvent(
    sessionId: Value[String],
    projectId: Value[String],
    data: BaseValue[OperationEnvelope],
    background: BaseValue[Boolean] = Env.background.getOrElse(Value(false)),
    `type`: Value[String] = str("message"),
    source: Value[String] = str("workflows.tools.CliTools")
  )

  def postEvent(data: OperationEnvelope, source: Value[String]) = {
    // POST to /workflows/events instead of Pub/Sub
    val ingestBody = RelayEvent(
      sessionId = Env.sessionId,
      projectId = Env.projectId,
      data = obj(data),
      background = Env.background.getOrElse(Value(false)),
      `type` = str("message"),
      source = str("workflows.tools.CliTools")
    )

    post[RelayEvent, NoValueT](
      "relay_ingest_tool_call",
      "events",
      obj(ingestBody)
    )
  }

  // Fire-and-forget enqueue for non-tool assistant content or status updates via awfl-relay
  def enqueueResponse(
    opName: String,
    callback_id: Value[String],
    content: Value[String],
    toolCall: BaseValue[ToolCall],
    cost: BaseValue[Double],
    background: BaseValue[Boolean] = Env.background.getOrElse(Value(false)),
    // Optional status and error for status updates
    status: Value[String] = str(""),
    error: Value[String] = Value.nil,
  ): Step[NoValueT, BaseValue[NoValueT]] = {
    val opEnvelopeField = OperationEnvelope(
      create_time = Value("sys.now()"),
      callback_id = callback_id,
      content = content,
      tool_call = toolCall,
      cost = cost,
      background = background,
      status = status,
      error = error
    )

    // POST to awfl-relay ingest endpoint: /workflows/events
    val ingest = postEvent(
      data = opEnvelopeField,
      source = str(opName)
    )

    Block(s"${opName}_enqueue_block", List(ingest) -> Value.nil)
  }
}