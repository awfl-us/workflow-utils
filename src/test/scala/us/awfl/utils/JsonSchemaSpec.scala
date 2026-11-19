package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.auto.given
import us.awfl.ista.ConvoSummary
import scala.compiletime.testing.typeCheckErrors

/** Standalone assertion runner: workflow-utils intentionally has no test library dependency. */
object JsonSchemaSpec:
  case class ToolArgs(path: Value[String], maxLines: Value[Int], note: OptBase[String],
                      tags: OptList[String], history: ListValue[Value[Boolean]])
  case class Plain(name: String, retries: Option[Int], labels: Map[String, List[String]])
  sealed trait Result
  case class Ok(value: String) extends Result
  case class Failure(reason: String) extends Result

  private def fields(schema: JsonSchema.Schema): Map[String, Map[String, Any]] =
    schema.toJsonValue("properties").asInstanceOf[Map[String, Map[String, Any]]]

  def main(args: Array[String]): Unit =
    val typed = JsonSchema.derive[ToolArgs].toJsonValue
    assert(typed("type") == "object")
    assert(typed("required").asInstanceOf[List[String]].toSet == Set("path", "maxLines", "history"))
    val properties = fields(JsonSchema.derive[ToolArgs])
    assert(properties("path")("type") == "string")
    assert(properties("maxLines")("type") == "integer")
    assert(properties("history")("type") == "array")
    assert(properties("history")("items") == Map("type" -> "boolean"))
    assert(properties("note")("oneOf") == List(Map("type" -> "string"), Map("type" -> "null")))
    assert(properties("tags")("oneOf") == List(
      Map("type" -> "array", "items" -> Map("type" -> "string")), Map("type" -> "null")))
    val plain = JsonSchema.derive[Plain].toJsonValue
    assert(plain("required") == List("name", "labels"))
    val plainFields = fields(JsonSchema.derive[Plain])
    assert(plainFields("labels")("type") == "object")
    assert(plainFields("labels")("additionalProperties") ==
      Map("type" -> "array", "items" -> Map("type" -> "string")))
    assert(JsonSchema.derive[Result].toJsonValue("oneOf").asInstanceOf[List[?]].size == 2)
    assert(JsonSchema.document[ToolArgs]("$schema") == "https://json-schema.org/draft/2020-12/schema")
    val unsupported = typeCheckErrors("us.awfl.utils.JsonSchema.derive[java.time.Instant]")
    assert(unsupported.nonEmpty, "unsupported JVM classes should be rejected at compile-time")
    assert(unsupported.exists(_.message.contains("JsonSchema cannot derive an unsupported type")))
    val modelTool = JsonSchema.function[ToolArgs]("READ_TYPED", "Typed file reader")
    val model = modelTool.toJsonValue
    assert(model.keySet == Set("type", "function"))
    val fn = model("function").asInstanceOf[Map[String, Any]]
    assert(fn.keySet == Set("name", "description", "parameters"))
    assert(fn("name") == "READ_TYPED")
    assert(fn("description") == "Typed file reader")
    val params = fn("parameters").asInstanceOf[Map[String, Any]]
    assert(params("additionalProperties") == false)
    assert(params("required") == List("path", "maxLines", "history"))
    assert(params("properties").asInstanceOf[Map[String, Any]]("note") ==
      Map("oneOf" -> List(Map("type" -> "string"), Map("type" -> "null"))))
    val routed = JsonSchema.RoutedTool(modelTool, "internal-workflows/secret-route")
    assert(!routed.toModelJsonValue.toString.contains("secret-route"))
    val notAnObject = try
      JsonSchema.function[String]("BAD", "not an object")
      false
    catch case _: IllegalArgumentException => true
    assert(notAnObject)

    val kala: KalaVibhaga = SegKala(
      sessionId = Value(CelStr("test-session")),
      end = Value[Double](0), windowSeconds = Value[Double](1200))
    given KalaVibhaga = kala
    val baseIsta = summon[Ista[ConvoSummary]]
    val contextual = Ista[ConvoSummary](baseIsta.name, baseIsta.build, {
      case _: SegKala => "Segment summary"
      case _: SessionKala => "Session summary"
      case _: WeekKala => "Week summary"
      case _: TermKala => "Term summary"
    })
    val contextualTool = JsonSchema.functionForIsta[ConvoSummary](contextual)
    assert(contextualTool.description == "Segment summary")
    assert(contextualTool.name == "summaries")
    assert(contextualTool.parameters("type") == "object")
    assert(contextual.description(SessionKala(Value(CelStr("s")), Value[Double](0))) == "Session summary")
    assert(JsonSchema.legacyProperty(JsonSchema.PrimitiveSchema("string")).`type` == "string")
    assert(JsonSchema.legacyProperty(JsonSchema.ArraySchema(JsonSchema.PrimitiveSchema("integer"))).`type` == "array")
    for schema <- List(
      JsonSchema.derive[ToolArgs], JsonSchema.derive[Option[String]],
      JsonSchema.derive[Map[String, Int]]) do
      val fails = try
        JsonSchema.legacyProperty(schema)
        false
      catch case _: IllegalArgumentException => true
      assert(fails, s"Legacy adapter silently downgraded $schema")
    println("AWFL_JSON_SCHEMA_PASS primitives/DSL wrappers/options/maps/sums/contextual/legacy-fail-closed")
