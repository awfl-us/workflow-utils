package us.awfl.ista

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.utils.Ista
import us.awfl.utils.Yoj
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Convo.StepName
import us.awfl.utils.SegKala
import us.awfl.utils.Convo

case class ConvoSummary(summary: Field)

object ConvoSummary:
  given Ista[ConvoSummary] = Ista("summaries", buildList("buildConvoSummaryIsta", List(ChatMessage("system", str(
    """Summarize the convo, return a JSON in this format:
    { "summary": "(Summary here)" }

    Don't repeat information already contained in previous summaries and extracted info.
    """.stripMargin
  )))))

  given Yoj[ConvoSummary] = Yoj("summaries", "Summary of older conversation messages:\r")

case class ToolCallFunction(name: BaseValue[String], arguments: BaseValue[String]) {
  def arg(name: String): Value[String] = Value(CelFunc(
    "map.get",
    CelFunc("json.decode", arguments.cel),
    name
  ))
}
case class ToolCall(id: BaseValue[String], `type`: String, function: BaseValue[ToolCallFunction])

case class ChatMessage(
  role: BaseValue[String],
  content: BaseValue[String],
  tool_calls: ListValue[ToolCall] = ListValue.empty,
  tool_call_id: BaseValue[String] = Value("null"),
  create_time: BaseValue[Double] = Value("sys.now()"),
  // When includeDocId=true on TopicContextYoj, messages sourced from Firestore may include docId.
  // Use Value("null") to omit when not provided.
  docId: BaseValue[String] = Value("null")
)

object ChatMessage:
  def apply(role: String, content: BaseValue[String]): ChatMessage = ChatMessage(obj(role), content)

  given Ista[ChatMessage] = Ista("messages", buildList("buildChatMessageYoj", List(ChatMessage("system", str("Return a message")))))

  given Yoj[ChatMessage] = Yoj("messages", "")
