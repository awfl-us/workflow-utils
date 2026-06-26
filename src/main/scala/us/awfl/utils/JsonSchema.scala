package us.awfl.utils

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, constValue, summonInline, summonFrom}

// AWFL DSL wrappers support
import us.awfl.dsl.{BaseValue as DslBaseValue, Value as DslValue, ListValue as DslListValue, Obj as DslObj, OptBase as DslOptBase, OptList as DslOptList}

// Kala/Ista for contextual descriptions (required)
import us.awfl.utils.{Ista, KalaVibhaga}

/**
 * JSON Schema derivation (draft 2020-12 style) that REQUIRES a contextual KalaVibhaga and
 * enriches every node with Ista-based descriptions when available.
 *
 * Supported:
 * - Primitives: string, boolean, integer, number
 * - Option[T] / OptBase[T] / OptList[T]: optional inside case classes (not in required[]);
 *   top-level Option[T] -> oneOf(inner, null); OptBase[T] -> inner; OptList[T] -> array(inner)
 * - Collections (List/Seq/Set/Vector/Array): array with items = schema(T)
 * - Map[String, V]: object with additionalProperties = schema(V)
 * - Case classes: object with properties/required inferred; optional wrappers are unwrapped for property schema
 * - Sealed traits: oneOf of member schemas (no discriminator)
 * - AWFL DSL wrappers: Value[T] and BaseValue[T] map to schema(T); ListValue[T] maps to array of schema(T)
 *
 * Usage:
 *   import us.awfl.utils.JsonSchema
 *   given kala: KalaVibhaga = ...
 *   case class Task(id: String, title: String, status: Option[String])
 *   val json = JsonSchema.documentJsonWithKala[Task]()
 */
object JsonSchema:

  // ----- Schema ADT -----
  sealed trait Schema:
    def toJsonValue: Map[String, Any]
    def toJson(pretty: Boolean = true): String = Json.render(toJsonValue, pretty)

  final case class ObjectSchema(
    properties: Map[String, Schema],
    required: List[String] = Nil,
    additionalProperties: Option[Schema] = None,
    title: Option[String] = None,
    description: Option[String] = None
  ) extends Schema:
    def toJsonValue: Map[String, Any] =
      val base: Map[String, Any] = Map(
        "type" -> "object",
        "properties" -> properties.view.mapValues(_.toJsonValue).toMap
      )
      val withReq = if required.nonEmpty then base + ("required" -> required) else base
      val withAP  = additionalProperties.fold(withReq)(ap => withReq + ("additionalProperties" -> ap.toJsonValue))
      val withT   = title.fold(withAP)(t => withAP + ("title" -> t))
      val withD   = description.fold(withT)(d => withT + ("description" -> d))
      withD

  final case class ArraySchema(items: Schema) extends Schema:
    def toJsonValue: Map[String, Any] = Map(
      "type" -> "array",
      "items" -> items.toJsonValue
    )

  final case class StringSchema(format: Option[String] = None, `enum`: Option[List[String]] = None) extends Schema:
    def toJsonValue: Map[String, Any] =
      val base = Map("type" -> "string")
      val withFmt = format.fold(base)(f => base + ("format" -> f))
      `enum`.fold(withFmt)(e => withFmt + ("enum" -> e))

  final case class BooleanSchema() extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> "boolean")

  final case class IntegerSchema() extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> "integer")

  final case class NumberSchema() extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> "number")

  final case class NullSchema() extends Schema:
    def toJsonValue: Map[String, Any] = Map("type" -> "null")

  final case class OneOfSchema(variants: List[Schema]) extends Schema:
    def toJsonValue: Map[String, Any] = Map("oneOf" -> variants.map(_.toJsonValue))

  final case class MapSchema(values: Schema) extends Schema:
    def toJsonValue: Map[String, Any] = Map(
      "type" -> "object",
      "additionalProperties" -> values.toJsonValue
    )

  // Lightweight wrapper to attach a description to any Schema node without changing its structure
  final case class Described(inner: Schema, description: String) extends Schema:
    def toJsonValue: Map[String, Any] =
      val base = inner.toJsonValue
      // Do not override if inner already supplies a description (e.g., ObjectSchema.description)
      if base.contains("description") then base else base + ("description" -> description)

  // ----- Contextual (Kala/Ista) derivation ONLY -----
  /** Optionally summon an Ista[T] and return its contextual description. */
  private inline def summonIstaDesc[T](using kala: KalaVibhaga): Option[String] =
    summonFrom {
      case ista: Ista[T] => Some(ista.description(kala))
      case _             => None
    }

  /** Attach a contextual description to the provided schema node if an Ista[T] is available. */
  private inline def maybeDescribe[T](schema: Schema)(using kala: KalaVibhaga): Schema =
    summonIstaDesc[T].fold(schema)(desc => Described(schema, desc))

  /** Derive a JSON Schema for type T, enriching each node with Ista-based descriptions when available. */
  inline def deriveWithKala[T](using kala: KalaVibhaga): Schema = inline erasedValue[T] match
    // AWFL DSL wrappers (order matters)
    case _: DslListValue[t] => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: DslOptList[t]   => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: DslValue[t]     => maybeDescribe[T](deriveWithKala[t])
    case _: DslBaseValue[t] => maybeDescribe[T](deriveWithKala[t])
    case _: DslObj[t]       => maybeDescribe[T](deriveWithKala[t])

    // Optional wrappers
    case _: Option[t]       => maybeDescribe[T](OneOfSchema(List(deriveWithKala[t], NullSchema())))
    case _: DslOptBase[t]   => maybeDescribe[T](deriveWithKala[t])

    // Standard Scala types
    case _: String    => maybeDescribe[T](StringSchema())
    case _: Boolean   => maybeDescribe[T](BooleanSchema())
    case _: Byte | _: Short | _: Int | _: Long => maybeDescribe[T](IntegerSchema())
    case _: Float | _: Double | _: BigDecimal  => maybeDescribe[T](NumberSchema())
    case _: List[t]   => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: Seq[t]    => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: Set[t]    => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: Vector[t] => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: Array[t]  => maybeDescribe[T](ArraySchema(deriveWithKala[t]))
    case _: Map[String, v] => maybeDescribe[T](MapSchema(deriveWithKala[v]))
    case _ => summonMirrorOrFallbackWithKala[T]

  private inline def summonMirrorOrFallbackWithKala[T](using kala: KalaVibhaga): Schema =
    summonFrom {
      case m: Mirror.ProductOf[T] => maybeDescribe[T](deriveProductWithKala[T](using m, kala))
      case m: Mirror.SumOf[T]     => maybeDescribe[T](deriveSumWithKala[T](using m, kala))
      case _                      => maybeDescribe[T](StringSchema())
    }

  private inline def deriveProductWithKala[T](using m: Mirror.ProductOf[T], kala: KalaVibhaga): Schema =
    val fieldSchemas = summonSchemasWithKala[m.MirroredElemTypes]
    val labels       = summonLabels[m.MirroredElemLabels]
    val required     = requiredFlags[m.MirroredElemTypes]
    val props: Map[String, Schema] = labels.zip(fieldSchemas).toMap
    val reqNames: List[String] = labels.zip(required).collect { case (n, true) => n }
    val title = Some(constValue[m.MirroredLabel])
    ObjectSchema(properties = props, required = reqNames, title = title)

  private inline def deriveSumWithKala[T](using m: Mirror.SumOf[T], kala: KalaVibhaga): Schema =
    val variants = summonSumSchemasWithKala[m.MirroredElemTypes]
    OneOfSchema(variants)

  private inline def summonSchemasWithKala[T <: Tuple](using kala: KalaVibhaga): List[Schema] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: t)   => unwrapOptionalSchemaWithKala[h] :: summonSchemasWithKala[t]

  // Unwrap optional wrappers for product fields so optionality is represented via required[] only.
  private inline def unwrapOptionalSchemaWithKala[T](using kala: KalaVibhaga): Schema = inline erasedValue[T] match
    case _: Option[x]     => deriveWithKala[x]
    case _: DslOptBase[x] => deriveWithKala[x]
    case _: DslOptList[x] => ArraySchema(deriveWithKala[x])
    case _                => deriveWithKala[T]

  private inline def summonSumSchemasWithKala[T <: Tuple](using kala: KalaVibhaga): List[Schema] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: t)   => deriveWithKala[h] :: summonSumSchemasWithKala[t]

  // ----- Shared helpers -----
  private inline def requiredFlags[T <: Tuple]: List[Boolean] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: t)   => (!isOptional[h]) :: requiredFlags[t]

  private inline def isOptional[T]: Boolean = inline erasedValue[T] match
    case _: Option[?]     => true
    case _: DslOptBase[?] => true
    case _: DslOptList[?] => true
    case _                => false

  private inline def summonLabels[T <: Tuple]: List[String] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: t)   => constValue[h].asInstanceOf[String] :: summonLabels[t]

  // ----- Titles -----
  inline def titleFor[T]: String = summonInlineTitle[T]

  private inline def summonInlineTitle[T]: String =
    summonInlineTitleMirror[T]
      .getOrElse(simpleRuntimeName[T])

  private inline def summonInlineTitleMirror[T]: Option[String] =
    summonFrom {
      case m: Mirror.Of[T] => Some(constValue[m.MirroredLabel])
      case _               => None
    }

  // Best-effort fallback if T has no mirror (primitives etc.).
  private def simpleRuntimeName[T]: String = "T"

  // ----- Rendering -----
  private object Json:
    def render(value: Any, pretty: Boolean): String =
      if pretty then renderPretty(value, 0)
      else renderCompact(value)

    private def renderCompact(value: Any): String = value match
      case m: Map[?, ?] =>
        val pairs = m.asInstanceOf[Map[String, Any]].map { case (k, v) => s"\"${escape(k)}\":${renderCompact(v)}" }
        pairs.mkString("{", ",", "}")
      case l: Iterable[?] => l.asInstanceOf[Iterable[Any]].map(renderCompact).mkString("[", ",", "]")
      case s: String => s"\"${escape(s)}\""
      case b: Boolean => b.toString
      case n: (Byte | Short | Int | Long | Float | Double | BigDecimal) => n.toString
      case null => "null"
      case sch: Schema => renderCompact(sch.toJsonValue)
      case other => s"\"${escape(other.toString)}\"" // fallback

    private def renderPretty(value: Any, indent: Int): String = value match
      case m: Map[?, ?] if m.isEmpty => "{}"
      case m: Map[?, ?] =>
        val map = m.asInstanceOf[Map[String, Any]]
        val i  = "  " * indent
        val i2 = "  " * (indent + 1)
        val body = map.map { case (k, v) => s"${i2}\"${escape(k)}\": ${renderPretty(v, indent + 1)}" }.mkString(",\n")
        s"{\n${body}\n${i}}"
      case l: Iterable[?] if l.isEmpty => "[]"
      case l: Iterable[?] =>
        val i  = "  " * indent
        val i2 = "  " * (indent + 1)
        val body = l.asInstanceOf[Iterable[Any]].map(v => s"${i2}${renderPretty(v, indent + 1)}").mkString(",\n")
        s"[\n${body}\n${i}]"
      case s: String => s"\"${escape(s)}\""
      case b: Boolean => b.toString
      case n: (Byte | Short | Int | Long | Float | Double | BigDecimal) => n.toString
      case null => "null"
      case sch: Schema => renderPretty(sch.toJsonValue, indent)
      case other => s"\"${escape(other.toString)}\""

    private def escape(s: String): String =
      s.flatMap {
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\b' => "\\b"
        case '\f' => "\\f"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c if c.isControl => f"\\u${c.toInt}%04x"
        case c => c.toString
      }

  // ----- Document builders (Kala-aware ONLY) -----
  inline def documentForWithKala[T](using kala: KalaVibhaga): Map[String, Any] =
    val core = deriveWithKala[T]
    val title = titleFor[T]
    Map(
      "$schema" -> "https://json-schema.org/draft/2020-12/schema",
      "title"   -> title,
      "type"    -> (core.toJsonValue.getOrElse("type", "object")),
    ) ++ (core.toJsonValue - "type")

  inline def documentJsonWithKala[T](pretty: Boolean = true)(using kala: KalaVibhaga): String =
    Json.render(documentForWithKala[T], pretty)

end JsonSchema
