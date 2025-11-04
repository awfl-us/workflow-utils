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
  case class ToolDefProperty(`type`: String, `enum`: Field = Field("[]"))
  case class ToolDefParams(`type`: String, properties: Map[String, ToolDefProperty], required: Field)
  case class ToolFunctionDef(name: BaseValue[String], description: String, parameters: ToolDefParams)
  case class Tool(`type`: String = "function", function: ToolFunctionDef)

  // Align with server: use max_completion_tokens
  case class ChatArgs(
    messages: ListValue[ChatMessage],
    model: BaseValue[String],
    temperature: Double,
    max_completion_tokens: BaseValue[Int],
    response_format: ResponseFormat,
    tools: ListValue[Tool] = ListValue.empty,
    tool_choice: BaseValue[String] = Value("null")
  )

  // Server returns { result, usage, total_cost }
  case class ChatResponse(result: Value[Reply], total_cost: BaseValue[Double])
  case class ChatJsonResponse[T](result: Value[T], total_cost: BaseValue[Double])
  case class ChatToolResponse(message: Value[ChatMessage], total_cost: BaseValue[Double])

  // Text mode (result.reply)
  def chat(
    name: String,
    messages: ListValue[ChatMessage],
    model: BaseValue[String] = Field.str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = obj(1024)
  ): Post[ChatResponse] = {
    // Omit response_format for normal text mode to avoid unsupported param issues
    val body = ChatArgs(messages, model, temperature, maxTokens, ResponseFormat("text"))
    post[ChatArgs, ChatResponse](name, "llm/chat", obj(body), Auth())
  }

  // JSON mode (result is parsed JSON object)
  def chatJson[T: Spec](
    name: String,
    messages: ListValue[ChatMessage],
    model: BaseValue[String] = Field.str("gpt-5"),
    temperature: Double = 0.8
  ): Step[ChatJsonResponse[T], BaseValue[ChatJsonResponse[T]]] = {
    val schema = ChatMessage(
      role = "system",
      content = str(("Respond with a JSON in this format:\r": Cel) + str(obj(implicitly[Spec[T]].init(Resolver("example")))))
    )
    val buildSchemaList = buildList(s"${name}_buildSchemaList", List(schema))
    val context = ListValue[ChatMessage](Resolver(CelPath(CelFunc("list.concat", messages.cel, buildSchemaList.resultValue(0).cel) :: Nil)))

    val body = ChatArgs(context, model, temperature, Value("null"), ResponseFormat("json_object"))
    val postStep = post[ChatArgs, ChatToolResponse](s"${name}_post", "llm/chat", obj(body), Auth()).flatMap(_.body)
    val result = ChatJsonResponse(Value(CelFunc("json.decode", postStep.result.message.flatMap(_.content))), postStep.result.total_cost)
    Block(s"${name}_block", List[Step[_, _]](buildSchemaList, postStep) -> obj(result))
  }

  // Tool-enabled chat (surface tool_calls)
  def chatWithTools(
    name: String,
    messages: ListValue[ChatMessage],
    tools: ListValue[Tool],
    tool_choice: BaseValue[String] = Field.str("auto"),
    model: BaseValue[String] = Field.str("gpt-4o"),
    temperature: Double = 0.8,
    maxTokens: BaseValue[Int] = Value.nil
  ): Step[ChatToolResponse, BaseValue[ChatToolResponse]] = {
    val toolChoice = Value[String](CelFunc("if", (tools.cel !== Cel.nil) && (CelFunc("len", tools) > 0), tool_choice.cel, Cel.nil))
    val body = ChatArgs(messages, model, temperature, maxTokens, ResponseFormat("text"), tools, toolChoice)
    post[ChatArgs, ChatToolResponse](name, "llm/chat", obj(body), Auth()).flatMap(_.body)
  }
}
