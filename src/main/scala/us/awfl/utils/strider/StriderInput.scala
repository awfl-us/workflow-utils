package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.utils.Segments
import us.awfl.utils.{Env, ENV}
import us.awfl.services.Llm.ChatResponse
import us.awfl.ista.ChatMessage
import us.awfl.core.WorkflowId

/**
 * Strider workflow input parameters and handy accessors.
 */
case class StriderInput[P](
  agentId: WorkflowId[P, ChatResponse[ChatMessage]],
  segmentEnd: Value[Double] = Value.nil,
  windowSeconds: Value[Int] = Value(Segments.DefaultWindowSeconds),
  overlapSeconds: Value[Int] = Value(Segments.DefaultOverlapSeconds),
  env: BaseValue[Env] = ENV
)
