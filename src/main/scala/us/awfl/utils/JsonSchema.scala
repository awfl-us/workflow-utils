package us.awfl.utils

import scala.compiletime.{constValue, erasedValue, error, summonFrom}
import scala.deriving.Mirror
import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.services.Llm

/** In-memory JSON Schema derivation for AWFL model-facing tool arguments.
  *
  * This is intentionally independent of agent prompts, external persistence,
  * the historical schema-file generator and contextual Ista metadata. Schema
  * derivation is compile-time checked: unsupported types are rejected rather
  * than turned into runtime `sys.error` failures.
  */
object JsonSchema:
  sealed trait Schema:
    def toJsonValue: Map[String, Any]

  case class ObjectSchema(properties: Map[String, Schema], required: List[String],
                          title: Option[String] = None) extends Schema:
    def toJsonValue: Map[String, Any] =
      Map("type" -> "object", "properties" -> properties.view.mapValues(_.toJsonValue).toMap,
          "additionalProperties" -> false) ++
        (if required.isEmpty then Map.empty else Map("required" -> required)) ++
        title.map(t => Map("title" -> t)).getOrElse(Map.empty)

  case class ArraySchema(items: Schema) extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> "array", "items" -> items.toJsonValue)

  case class PrimitiveSchema(kind: String) extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> kind)

  case class MapSchema(values: Schema) extends Schema:
    def toJsonValue: Map[String, Any] =
      Map("type" -> "object", "additionalProperties" -> values.toJsonValue)

  case class OneOfSchema(variants: List[Schema]) extends Schema:
    def toJsonValue: Map[String, Any] = Map("oneOf" -> variants.map(_.toJsonValue))

  // Optional fields omit `required` and accept explicit JSON null.
  private inline def optional[T]: Boolean = inline erasedValue[T] match
    case _: Option[?] => true
    case _: OptBase[?] => true
    case _: OptList[?] => true
    case _ => false

  private inline def fieldSchema[T]: Schema = inline erasedValue[T] match
    case _: Option[t] => OneOfSchema(List(derive[t], PrimitiveSchema("null")))
    case _: OptBase[t] => OneOfSchema(List(derive[t], PrimitiveSchema("null")))
    case _: OptList[t] => OneOfSchema(List(ArraySchema(derive[t]), PrimitiveSchema("null")))
    case _ => derive[T]

  private inline def fields[T <: Tuple]: List[Schema] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: tail) => fieldSchema[h] :: fields[tail]

  private inline def labels[T <: Tuple]: List[String] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: tail) => constValue[h].asInstanceOf[String] :: labels[tail]

  private inline def requirements[T <: Tuple]: List[Boolean] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: tail) => (!optional[h]) :: requirements[tail]

  private inline def alternatives[T <: Tuple]: List[Schema] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: tail) => derive[h] :: alternatives[tail]

  private inline def mirrorSchema[T]: Schema = summonFrom {
    case m: Mirror.ProductOf[T] =>
      val names = labels[m.MirroredElemLabels]
      val present = requirements[m.MirroredElemTypes]
      ObjectSchema(names.zip(fields[m.MirroredElemTypes]).toMap,
        names.zip(present).collect { case (name, true) => name },
        Some(constValue[m.MirroredLabel]))
    case m: Mirror.SumOf[T] => OneOfSchema(alternatives[m.MirroredElemTypes])
    case _ => error("JsonSchema cannot derive an unsupported type; provide a supported case class, sealed trait, collection or primitive")
  }

  inline def derive[T]: Schema = inline erasedValue[T] match
    case _: ListValue[t] => ArraySchema(derive[t])
    case _: OptList[t] => OneOfSchema(List(ArraySchema(derive[t]), PrimitiveSchema("null")))
    case _: Value[t] => derive[t]
    case _: Obj[t] => derive[t]
    case _: BaseValue[t] => derive[t]
    case _: OptBase[t] => OneOfSchema(List(derive[t], PrimitiveSchema("null")))
    case _: Option[t] => OneOfSchema(List(derive[t], PrimitiveSchema("null")))
    case _: String => PrimitiveSchema("string")
    case _: Boolean => PrimitiveSchema("boolean")
    case _: Int | _: Long | _: Short | _: Byte => PrimitiveSchema("integer")
    case _: Float | _: Double | _: BigDecimal => PrimitiveSchema("number")
    case _: List[t] => ArraySchema(derive[t])
    case _: Vector[t] => ArraySchema(derive[t])
    case _: Seq[t] => ArraySchema(derive[t])
    case _: Set[t] => ArraySchema(derive[t])
    case _: Array[t] => ArraySchema(derive[t])
    case _: Map[String, t] => MapSchema(derive[t])
    case _ => mirrorSchema[T]

  inline def document[T]: Map[String, Any] =
    Map("$schema" -> "https://json-schema.org/draft/2020-12/schema") ++ derive[T].toJsonValue

  /** Exact model-facing function schema. Never include routing metadata here. */
  case class ModelFunction(name: String, description: String, parameters: Map[String, Any]):
    def toJsonValue: Map[String, Any] = Map(
      "type" -> "function",
      "function" -> Map("name" -> name, "description" -> description,
                        "parameters" -> parameters)
    )

  /** A separate server-side mapping; `workflowName` must never be passed to the model. */
  case class RoutedTool(function: ModelFunction, workflowName: String):
    def toModelJsonValue: Map[String, Any] = function.toJsonValue

  inline def function[T](name: String, description: String): ModelFunction =
    val parameters = derive[T].toJsonValue
    require(parameters.get("type").contains("object"), "Tool parameters must be a JSON object schema")
    ModelFunction(name, description, parameters)

  /** Pull contextual text from the existing Ista while deriving schema at the call site. */
  inline def functionForIsta[T: Spec](ista: Ista[T])(using kala: KalaVibhaga): ModelFunction =
    function[T](ista.name, ista.description(kala))

  /** Converts ONLY exactly representable schema nodes into the legacy Llm.ToolDefProperty.
    * Legacy properties cannot encode `oneOf`, nullability or typed map values.
    * Returning a weaker schema here would let model arguments escape validation.
    */
  def legacyProperty(schema: Schema): Llm.ToolDefProperty = schema match
    // The legacy shape has no `additionalProperties` field: even a plain
    // case-class object would lose its additionalProperties=false constraint.
    // Never silently downgrade it to a permissive object.
    case ArraySchema(items) => Llm.ToolDefProperty(
      `type` = "array", items = Some(legacyProperty(items).toMap)
    )
    case PrimitiveSchema(kind) if kind != "null" => Llm.ToolDefProperty(kind)
    case other => throw new IllegalArgumentException(
      s"Llm.ToolDefProperty cannot encode ${other.getClass.getSimpleName} losslessly; use ModelFunction.toJsonValue")

  /** New model-facing tool objects use `function`; the legacy property
    * converter is restricted to scalar and array nodes until the older LLM
    * class is upgraded to represent strict JSON Schema objects and unions.
    */
