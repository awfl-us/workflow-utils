package us.awfl.services

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.utils._

object Llm {
  case class ResponseFormat(`type`: String)
  case class Reply(reply: String)

  // Tool definitions
  case class ToolDefProperty(
    `type`: String,
    properties: Map[String, ToolDefProperty] = Map.empty,
    `enum`: OptList[String] = OptList.nil,
    required: OptList[String] = OptList.nil,
    items: Option[Map[String, ?]] = None
  ) {
    def toMap: Map[String, ?] = Map("type" -> `type`, "properties" -> properties, "enum" -> `enum`, "required" -> required, "items" -> items)
  }
  
  case class ToolFunctionDef(name: Value[String], description: Value[String], parameters: BaseValue[ToolDefProperty])
  case class Tool(`type`: Value[String] = str("function"), function: BaseValue[ToolFunctionDef])

  sealed trait ToolChoice
  object ToolChoice {
    val auto: Value[ToolChoice] = Value(CelStr("auto"))
    case class ToolChoiceFunction(name: String)
    case class Function(function: ToolChoiceFunction, `type`: String = "function") extends ToolChoice
    object Function:
      def apply(name: String): Function = Function(ToolChoiceFunction(name))

    given Spec[ToolChoice] = Spec(_ => Function(ToolChoiceFunction("")))
  }

  // Align with server: use max_completion_tokens
  case class ChatArgs(
    messages: ListValue[ChatMessage],
    model: Value[String],
    temperature: Double,
    max_completion_tokens: BaseValue[Int],
    response_format: ResponseFormat,
    sessionId: Value[String],
    workflow_name: Value[String],
    tools: ListValue[Tool] = ListValue.empty,
    tool_choice: BaseValue[ToolChoice] = ToolChoice.auto
  )

  // Server returns { result, usage, total_cost }
  case class ChatResponse(result: Value[Reply], total_cost: Value[Double])
  case class ChatJsonResponse[T](result: Value[T], total_cost: Value[Double])
  case class ChatToolResponse(message: Value[ChatMessage], total_cost: Value[Double], usage: Value[Usage])

  // Text mode (result.reply)
  def chat(
    name: String,
    messages: ListValue[ChatMessage],
    sessionId: Value[String],
    model: Value[String] = str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = obj(1024),
  ): Post[ChatResponse] = {
    // Omit response_format for normal text mode to avoid unsupported param issues
    val body = ChatArgs(messages, model, temperature, maxTokens, ResponseFormat("text"), sessionId, WORKFLOW_ID)
    post[ChatArgs, ChatResponse](name, "llm/chat", obj(body), Auth())
  }

  // JSON mode (result is parsed JSON object)
  def chatJson[T: Spec](
    name: String,
    messages: ListValue[ChatMessage],
    sessionId: Value[String],
    model: Value[String] = str("gpt-5"),
    temperature: Double = 0.8
  ): Step[ChatJsonResponse[T], Value[ChatJsonResponse[T]]] = {
    val spec = Try("spec", List() -> str(CelValue(obj(implicitly[Spec[T]].init(Resolver("example"))))))
    val schema = ChatMessage(
      role = "system",
      content = str(("Respond with a JSON in this format:\r": Cel) + spec.resultValue.cel)
    )
    val buildSchemaList = buildList(s"${name}_buildSchemaList", List(schema))
    val context = ListValue[ChatMessage](Resolver(CelPath(CelFunc("list.concat", messages.cel, buildSchemaList.resultValue(0).cel) :: Nil)))

    val body = ChatArgs(context, model, temperature, Value("null"), ResponseFormat("json_object"), sessionId, WORKFLOW_ID)
    val postStep = post[ChatArgs, ChatToolResponse](s"${name}_post", "llm/chat", obj(body), Auth()).flatMap(_.body)
    val result = Try("decoded", List() -> obj(ChatJsonResponse(Value(CelFunc("json.decode", postStep.result.message.flatMap(_.content))), postStep.result.total_cost)))
    Block(s"${name}_block", List[Step[_, _]](spec, buildSchemaList, postStep, result) -> result.resultValue)
  }

  // Tool-enabled chat (surface tool_calls)
  def chatWithTools(
    name: String,
    messages: ListValue[ChatMessage],
    tools: ListValue[Tool],
    sessionId: Value[String],
    tool_choice: BaseValue[ToolChoice] = ToolChoice.auto,
    model: Value[String] = str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = Value.nil,
  ): Step[ChatToolResponse, Value[ChatToolResponse]] = {
    val toolChoice = Switch("toolChoice", List(
      ((tools.cel !== Cel.nil) && (CelFunc("len", tools) > 0)) ->  (List() -> tool_choice),
      (true: Cel) -> (List() -> Value.nil[ToolChoice])
    ))
    val body = ChatArgs(messages, model, temperature, maxTokens, ResponseFormat("text"), sessionId, WORKFLOW_ID, tools, toolChoice.resultValue)
    val request = post[ChatArgs, ChatToolResponse](name, "llm/chat", obj(body), Auth()).flatMap(_.body)
    Block("chatBlock", List[Step[_, _]](toolChoice, request) -> request.resultValue)
  }

  def usage(
    name: String,
    env: Env = Env.get
  ): Step[Stats, Value[Stats]] =
    get[UsageResponse](name, str(("llm/usage/": Cel) + env.sessionId + "/totals"), env = env)
      .flatMap(_.body).flatMap(_.overall)

  case class UsageResponse(
    overall: Value[Stats],
    by_workflow: Map[String, Stats]
  )

  case class Stats(
    requests: Value[Int],
    total_cost: Value[Double],
    usage: Value[Usage]
  )

  case class CompletionTokensDetails(
    accepted_prediction_tokens: Value[Double],
    audio_tokens: Value[Double],
    reasoning_tokens: Value[Double],
    rejected_prediction_tokens: Value[Double]
  ) {
    def +(o: CompletionTokensDetails): CompletionTokensDetails =
      CompletionTokensDetails(
        Value(accepted_prediction_tokens + o.accepted_prediction_tokens),
        Value(audio_tokens + o.audio_tokens),
        Value(reasoning_tokens + o.reasoning_tokens),
        Value(rejected_prediction_tokens + o.rejected_prediction_tokens)
      )
  }

  case class PromptTokensDetails(
    audio_tokens: Value[Double],
    cached_tokens: Value[Double]
  ) {
    def +(o: PromptTokensDetails): PromptTokensDetails =
      PromptTokensDetails(
        Value(audio_tokens + o.audio_tokens),
        Value(cached_tokens + o.cached_tokens)
      )
  }

  case class Usage(
    completion_tokens: Value[Double],
    completion_tokens_details: BaseValue[CompletionTokensDetails],
    prompt_tokens: Value[Double],
    prompt_tokens_details: BaseValue[PromptTokensDetails],
    total_tokens: Value[Double]
  ) {
    def plus(o: Value[Usage]): Step[Usage, BaseValue[Usage]] = {
      val buildCompleteDetails = Try("addCompleteDetails", List() -> completion_tokens_details)
      val buildPromptDetails = Try("addPromptDetails", List() -> prompt_tokens_details)
      Try("plusUsage", List[Step[?,?]](buildCompleteDetails, buildPromptDetails) -> obj(Usage(
        Value(completion_tokens + o.get.completion_tokens),
        obj(buildCompleteDetails.result + o.flatMap(_.completion_tokens_details).get),
        Value(prompt_tokens + o.get.prompt_tokens),
        obj(buildPromptDetails.result + o.flatMap(_.prompt_tokens_details).get),
        Value(total_tokens + o.get.total_tokens)
      )))
    }
  }

  // val zeroUsage = Usage(
  //   Value(0),
  //   obj(CompletionTokensDetails(Value(0), Value(0), Value(0), Value(0))),
  //   Value(0),
  //   obj(PromptTokensDetails(Value(0), Value(0))),
  //   Value(0)
  // )
}
