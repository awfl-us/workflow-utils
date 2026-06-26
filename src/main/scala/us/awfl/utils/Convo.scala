package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.services.Firebase.ListItem
import us.awfl.services.Llm.{ChatResponse, Tool}
import us.awfl.utils.Timestamped
import us.awfl.ista.ConvoSummary
import us.awfl.utils.TopicContextYoj
import us.awfl.utils.Yoj
import us.awfl.utils.YojComposer
import us.awfl.services.Llm.ToolChoice

object Convo {
  def addMessage(name: String, sessionId: Value[String], message: BaseValue[ChatMessage], cost: Value[Double] = Value.nil): Post[Nothing] = {
    val now = Value[Double](CelFunc("sys.now"))
    given KalaVibhaga = SegKala(sessionId, now, Value.nil)
    summon[Ista[ChatMessage]].write(name, message, now, cost)
  }

  val emptyYoj = Raise("emptyYoj", obj(Error(str("Empty Yoj returned"), OptValue[Int](400))))

  // def complete[In: Yoj, Out: Spec: Ista](
  //   name: String,
  //   prompt: ListValue[ChatMessage],
  //   yoj: ListValue[ChatMessage],
  //   sessionId: Value[String],
  //   model: Value[String] = str("gpt-5")
  // )(using kala: KalaVibhaga): Step[ChatResponse[Out], Value[ChatResponse[Out]]] = {
  //   val tool = summon[Ista[Out]].tool
  //   val messages = join(s"${name}_joinMessages", prompt, yoj)
  //   val chat = us.awfl.services.Llm.chatJson[Out](s"${name}_chat", messages.resultValue, sessionId, model = model)
  //   val switch = Switch(s"${name}_switch", List[(Cel, (List[Step[?, ?]], BaseValue[ChatResponse[Out]]))](
  //     (len(yoj) > 0) -> (List[Step[?, ?]](buildIsta, messages, chat) -> chat.resultValue),
  //     (true: Cel) -> (List(emptyYoj) -> Value.nil)
  //   ))
  //   Block(s"${name}_block", switch.fn)
  // }

  // Tool-enabled completion that surfaces tool_calls for external dispatch
  def completeWithTools(
    name: String,
    prompt: ListValue[ChatMessage],
    yoj: ListValue[ChatMessage],
    tools: ListValue[Tool],
    sessionId: Value[String],
    toolChoice: BaseValue[ToolChoice] = ToolChoice.auto,
    model: Value[String] = str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = Value("null")
  ): Step[ChatResponse[ChatMessage], Value[ChatResponse[ChatMessage]]] = {
    val messages = join(s"${name}_joinMessages", prompt, yoj)
    val chat = us.awfl.services.Llm.chatWithTools(
      s"${name}_chat",
      messages.resultValue,
      tools,
      sessionId,
      tool_choice = toolChoice,
      model = model,
      temperature = temperature,
      maxTokens = maxTokens
    )
    val switch = Switch(s"${name}_switch", List(
      (len(yoj) > 0) -> (List[Step[?, ?]](messages, chat) -> chat.resultValue),
      (true: Cel) -> (List(emptyYoj) -> Value.nil)
    ))
    Block(s"${name}_block", switch.fn)
  }

  opaque type SessionId = Value[String]
  object SessionId:
    def apply(sessionId: Value[String]): SessionId = sessionId
  extension(sessionId: SessionId)
    def value: Value[String] = sessionId

  opaque type StepName = String
  object StepName:
    def apply(name: String): StepName = name
  extension(name: StepName)
    def value: String = name

  // ConvoContext is now a concrete case class to enable schema derivation.
  // The Yoj below still composes the summarized/tail components, but the data type is concrete.
  case class ConvoContext(summarized: ConvoSummary, tail: ListValue[ChatMessage])
  object ConvoContext:
    // Parent Yoj that builds a ComponentSpec with two children (summaries and tail)
    given (using summarized: Yoj[ConvoSummary], tail: Yoj[ChatMessage]): Yoj[ConvoContext] =
      Yoj(
        "convoContext",
        // buildYoj: render this component (with children) via TopicContextYoj
        { kala =>
          // given KalaVibhaga = kala

          YojComposer.composed(
            name = "convoContext",
            childComponents = List(summarized.component, tail.component),
            kala = kala,
            intro = None,
            promoteUpstream = true,
            // Preserve prior behavior where filters was explicitly null
            filters = ListValue.nil
          )
        },
        // componentYoj: static parent spec with nested children
        YojComposer.parentComponent(
          name = "convoContext",
          children = List(summarized.component, tail.component)
        )
      )
}
