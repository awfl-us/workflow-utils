package us.awfl.utils

import us.awfl.dsl.{Value, OptBase, OptList, ListValue}
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
    println("AWFL_JSON_SCHEMA_PASS primitives/DSL wrappers/options/collections/maps/sums/unsupported-type")
