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
    collection: Value[String],
    id: Value[String],
    thresholdMillis: Int,
    step: Step[T, BaseValue[T]]
  ): Step[T, Value[T]] = {
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
        updatedAt = Value(nowField.value)
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
        (List(Raise(s"${name}_raiseFailedCache", obj(Error(str("Cache run failed"), Value(500))))) -> step.resultValue)
    ))

    Block(name, List[Step[_, _]](readStep, conditionalRun) -> conditionalRun.resultValue)
  }

  case class CachedValueWrite[T](updatedAt: Value[String], result: BaseValue[T])
  case class CachedValue[T](updatedAt: Value[String], result: Resolved[T])

  implicit def cachedSpec[T: Spec]: Spec[CachedValue[T]] = Spec { resolver =>
    CachedValue[T](resolver.in("updatedAt"), resolver.in("result"))
  }
}
