package us.awfl.utils

import scala.compiletime.{constValue, erasedValue, error, summonFrom}
import scala.deriving.Mirror
import us.awfl.dsl.{BaseValue, Value, ListValue, Obj, OptBase, OptList}

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
