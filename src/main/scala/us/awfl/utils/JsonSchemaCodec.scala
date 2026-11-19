package us.awfl.utils

/** Canonical JSON serialization for the in-memory model-facing schema.
  * Object keys are sorted recursively so repeated builds produce identical
  * request payloads (and stable OpenAI prompt-cache prefixes).
  */
object JsonSchemaCodec:
  def render(value: Any): String = value match
    case null => "null"
    case s: String => "\"" + s.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case c if c < ' ' => f"\\u${c.toInt}%04x"
      case c => c.toString
    } + "\""
    case b: Boolean => b.toString
    case n: BigDecimal => n.bigDecimal.stripTrailingZeros.toPlainString
    case n: Double if n.isNaN || n.isInfinity =>
      throw new IllegalArgumentException("Nonfinite JSON number")
    case n: Float if n.isNaN || n.isInfinity =>
      throw new IllegalArgumentException("Nonfinite JSON number")
    case n: Number => n.toString
    case m: scala.collection.Map[?, ?] =>
      val entries = m.toSeq.map {
        case (k: String, v) => (k, v)
        case (null, _) => throw new IllegalArgumentException("JSON object keys cannot be null")
      }.sortBy(_._1)
      entries.map { case (k, v) => render(k) + ":" + render(v) }.mkString("{", ",", "}")
    case values: Iterable[?] => values.map(render).mkString("[", ",", "]")
    case values: Array[?] => values.iterator.map(render).mkString("[", ",", "]")
    case unknown => throw new IllegalArgumentException(s"Unsupported JSON value: ${unknown.getClass.getName}")

  def function(tool: JsonSchema.ModelFunction): String = render(tool.toJsonValue)
  def routed(tool: JsonSchema.RoutedTool): String = render(tool.toModelJsonValue)
