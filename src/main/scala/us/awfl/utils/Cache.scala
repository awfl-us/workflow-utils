package us.awfl.utils

import io.circe.Encoder
import io.circe.generic.auto._
import us.awfl.dsl.*
import us.awfl.dsl.Cel.*
import us.awfl.dsl.CelOps.*
import us.awfl.services.Firebase

object Cache {
  def apply[T: Spec](
    name: String,
    collection: BaseValue[String],
    id: Field,
    thresholdMillis: Int,
    step: Step[T, BaseValue[T]]
  ): Step[T, BaseValue[T]] with ValueStep[T] = {
    import Cache._

    val readStep = Firebase.read[CachedValue[T]](s"${name}Read", collection, str(id.cel))
    val readBody = readStep.resultValue.flatMap(_.body)

    val nowField = CelConst("sys.now()")

    val updateStep = Firebase.update(
      name = s"${name}Write",
      collection = collection,
      id = id,
      contents = obj(CachedValueWrite(
        result = step.resultValue,
        updatedAt = Field(nowField.value)
      ))
    )

    val conditionalRun = Switch(s"${name}IfStale", List(
      (
        !("body" in readStep.resultValue.cel) ||
        !("updatedAt" in readBody.cel) ||
        ((nowField - readBody.flatMap(_.updatedAt).cel) > thresholdMillis)
      ) ->
        (List[Step[_, _]](step, updateStep) -> step.resultValue),

      ("body" in readStep.resultValue.cel) ->
        (Nil -> readBody.flatMap(_.result)),

      (true: Cel) ->
        (List(Raise(s"${name}_raiseFailedCache", obj(Error(obj("Cache run failed"), obj(""))))) -> step.resultValue)
    ))

    Block(name, List[Step[_, _]](readStep, conditionalRun) -> conditionalRun.resultValue)
  }

  case class CachedValueWrite[T](updatedAt: Field, result: BaseValue[T])
  case class CachedValue[T](updatedAt: Field, result: Resolved[T])

  implicit def cachedSpec[T: Spec]: Spec[CachedValue[T]] = Spec { resolver =>
    CachedValue[T](resolver.field("updatedAt"), resolver.in("result"))
  }

  // implicit val strValEncoder: Encoder[Value[String]] = Encoder.forProduct1("value")(_.get)

  // implicit def encoder[T](using enc: Encoder[T]): Encoder[CachedValue[T]] =
  //   deriveEncoder
}
