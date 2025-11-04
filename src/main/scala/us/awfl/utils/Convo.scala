package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.services.Firebase.ListItem
import us.awfl.services.Llm.{ChatJsonResponse, ChatToolResponse, Tool}
import us.awfl.utils.Timestamped
import us.awfl.ista.ConvoSummary
import us.awfl.utils.TopicContextYoj
import us.awfl.utils.Yoj
import us.awfl.utils.YojComposer

object Convo {
  def addMessage(name: String, message: BaseValue[ChatMessage], cost: BaseValue[Double] = Value.nil)(using ista: Ista[ChatMessage], kala: KalaVibhaga): Post[Nothing] = {
    ista.write(name, message, Value("sys.now()"), cost)
  }

  val emptyYoj = Raise("emptyYoj", obj(Error(str("Empty Yoj returned"), str(400))))

  def complete[In: Yoj, Out: Spec: Ista](name: String, prompt: Prompt, yoj: ListValue[ChatMessage], model: BaseValue[String] = Field.str("gpt-5")): Step[ChatJsonResponse[Out], BaseValue[ChatJsonResponse[Out]]] = {
    val buildIsta = summon[Ista[Out]].build
    val messages = join(s"${name}_joinMessages", prompt.build.resultValue, yoj, buildIsta.resultValue)
    val chat = us.awfl.services.Llm.chatJson[Out](s"${name}_chat", messages.resultValue, model = model)
    val switch = Switch(s"${name}_switch", List(
      (len(yoj) > 0) -> (List[Step[_, _]](buildIsta, prompt.build, messages, chat) -> chat.resultValue),
      (true: Cel) -> (List(emptyYoj) -> Value("null"))
    ))
    Block(s"${name}_block", switch.fn)
  }

  // Tool-enabled completion that surfaces tool_calls for external dispatch
  def completeWithTools(
    name: String,
    prompt: Prompt,
    yoj: ListValue[ChatMessage],
    tools: ListValue[Tool],
    toolChoice: BaseValue[String] = Field.str("auto"),
    model: BaseValue[String] = Field.str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = Value("null")
  ): Step[ChatToolResponse, BaseValue[ChatToolResponse]] = {
    val messages = join(s"${name}_joinMessages", prompt.build.resultValue, yoj)
    val chat = us.awfl.services.Llm.chatWithTools(
      s"${name}_chat",
      messages.resultValue,
      tools = tools,
      tool_choice = toolChoice,
      model = model,
      temperature = temperature,
      maxTokens = maxTokens
    )
    val switch = Switch(s"${name}_switch", List(
      (len(yoj) > 0) -> (List[Step[_, _]](prompt.build, messages, chat) -> chat.resultValue),
      (true: Cel) -> (List(emptyYoj) -> Value("null"))
    ))
    Block(s"${name}_block", switch.fn)
  }

  // Provide a session-scoped execution wrapper used by Strider and others
  def inSession[T: Spec](name: String, sessionId: BaseValue[String])(run: => (List[Step[_, _]], BaseValue[T])): Step[T, BaseValue[T]] = {
    // We could add logging or guards here later; for now just wrap the provided block.
    Block(name, run)
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

  case class Prompt(build: Step[ChatMessage, ListValue[ChatMessage]])

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
          given KalaVibhaga = kala

          YojComposer.composed(
            name = "convoContext",
            childComponents = List(summarized.component, tail.component),
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
