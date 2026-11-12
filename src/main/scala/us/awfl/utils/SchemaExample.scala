package us.awfl.utils

import io.circe.Json
import scala.util.Try
import us.awfl.dsl.*

/** Render an actual JSON example from a Spec-derived product shape.
  *
  * `Spec.init(Resolver("example"))` creates CEL references, not literal
  * example data. Serializing that structure as a workflow expression made
  * the resulting prompt depend on an unbound `example` variable. Instead,
  * create a JSON string at workflow definition time, preserving nested
  * products and representative list elements without runtime CEL references.
  */
object SchemaExample {
  def apply[T: Spec]: String = {
    val instance = summon[Spec[T]].init(Resolver("schemaExample"))
    encode(instance, 0).noSpaces
  }

  private def encode(value: Any, depth: Int): Json = {
    // A recursive schema cannot produce an infinitely recursive sample.
    if (depth > 16) return Json.Null
    value match {
      case list: ListValue[?] =>
        val sample = Try(list.spec.init(list.resolver.copy(safe = true))).toOption
        Json.arr(sample.fold[Json](Json.fromString("example"))(encode(_, depth + 1)))
      case resolved: Resolved[?] =>
        Try(resolved.spec.init(resolved.resolver.copy(safe = true)))
          .toOption.fold[Json](Json.fromString("example"))(encode(_, depth + 1))
      case _: OptResolved[?] => Json.Null
      case opt: OptObj[?] => encode(opt.obj.value, depth + 1)
      case Obj(v) => encode(v, depth + 1)
      case Some(v) => encode(v, depth + 1)
      case None => Json.Null
      case p: Product =>
        Json.obj(p.productElementNames.zip(p.productIterator)
          .map((name, field) => name -> encode(field, depth + 1)).toSeq*)
      case m: scala.collection.Map[?, ?] =>
        Json.obj(m.toSeq.collect { case (key: String, v) =>
          key -> encode(v, depth + 1)
        }*)
      case seq: Seq[?] => Json.arr(seq.map(encode(_, depth + 1))*)
      case null => Json.Null
      case v: String => Json.fromString(if (v.isEmpty) "example" else v)
      case v: Boolean => Json.fromBoolean(v)
      case v: Int => Json.fromInt(v)
      case v: Long => Json.fromLong(v)
      case v: Double => Json.fromDoubleOrNull(v)
      case v: Float => Json.fromFloatOrNull(v)
      case _ => Json.Null
    }
  }
}
