package us.awfl.ista

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.utils.Ista
import us.awfl.utils.Yoj
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.{SegKala, SessionKala, WeekKala, TermKala}
import us.awfl.utils.Convo.StepName
import us.awfl.utils.SegKala
import us.awfl.utils.Convo

case class ConvoSummary(title: Value[String], summary: Value[String])

object ConvoSummary:
  given Ista[ConvoSummary] = Ista("summaries", kala => buildList("buildConvoSummaryIsta", List(ChatMessage("system", str(
    """Summarize the convo, return a JSON in this format:
    { "title": "(short title for the convo, ommit if no information is provided)", "summary": "(Summary here)" }
    """.stripMargin
  )),ChatMessage("system", str(kala match {
    case _: SegKala => "Don't repeat information already contained in previous summaries and extracted info."
    case _: SessionKala => "Refine (or initiate) the whole session summary."
    case _: WeekKala => "Refine (or initiate) the whole week summary."
    case _: TermKala => "Refine (or initiate) the whole term summary."
  })))))

  given Yoj[ConvoSummary] = Yoj("summaries", "Summary of older conversation messages:\r")

case class ToolCallFunction(name: Value[String], arguments: Value[String]) {
  def arg(name: String): Value[String] = Value(CelFunc(
    "map.get",
    CelFunc("json.decode", arguments.cel),
    name
  ))
}
case class ToolCall(id: Value[String], `type`: String, function: BaseValue[ToolCallFunction])

case class ChatMessage(
  role: Value[String],
  content: Value[String],
  tool_calls: ListValue[ToolCall] = ListValue.empty,
  tool_call_id: Value[String] = Value("null"),
  create_time: Value[Double] = Value("sys.now()"),
  // When includeDocId=true on TopicContextYoj, messages sourced from Firestore may include docId.
  // Use Value("null") to omit when not provided.
  docId: Value[String] = Value("null")
)

object ChatMessage:
  def apply(role: String, content: Value[String]): ChatMessage = ChatMessage(str(role), content)

  given Ista[ChatMessage] = Ista("messages", _ => buildList("buildChatMessageYoj", List(ChatMessage("system", str("Return a message")))))

  given Yoj[ChatMessage] = Yoj("messages", "")
