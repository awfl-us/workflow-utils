package us.awfl.utils

import io.circe.Encoder
import io.circe.generic.auto._
import us.awfl.dsl.*
import us.awfl.dsl.Cel.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.services.Firebase

case class Cache(collection: Value[String]) {
  def apply[T: Spec](id: Value[String])(step: Step[T, Resolved[T]]): Step[T, Value[T]] = apply(
    s"cache_${step.name}",
    id
  )(step)

  case class ListWrapper[T](list: ListValue[T])
  def list[T: Spec](id: Value[String])(step: Step[T, ListValue[T]]): Step[T, ListValue[T]] = apply(
    s"cache_${step.name}",
    id
  ) { Try[ListWrapper[T], Obj[ListWrapper[T]]]("wrapList", List(step) -> obj(ListWrapper(step.resultValue)), reRaise = true) }
    .flatMapList(_.list)

  def apply[T: Spec](
    name: String,
    id: Value[String],
    thresholdMillis: Value[Int] = Value.nil
  )(step: Step[T, BaseValue[T]]): Step[T, Value[T]] = {
    import Cache._

    val readStep = Firebase.read[CachedValue[T]](s"${name}Read", collection, id)
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
        (readStep.resultValue === Cel.nil) ||
        !("body" in readStep.resultValue.cel) ||
        !("updatedAt" in readBody.cel) || (
          (thresholdMillis !== Cel.nil) &&
          ((nowField - readBody.flatMap(_.updatedAt).cel) > thresholdMillis)
        )
      ) ->
        (List[Step[_, _]](step, updateStep) -> step.resultValue),

      ("body" in readStep.resultValue.cel) ->
        (Nil -> readBody.flatMap(_.result)),

      (true: Cel) ->
        (List(Raise(s"${name}_raiseFailedCache", obj(Error(str("Cache run failed"), OptValue(Value[Int](500)))))) -> step.resultValue)
    ))

    Block(name, List[Step[_, _]](readStep, conditionalRun) -> conditionalRun.resultValue)
  }

  case class CachedValueWrite[T](updatedAt: Value[String], result: BaseValue[T])
  case class CachedValue[T](updatedAt: Value[String], result: Resolved[T])

  implicit def cachedSpec[T: Spec]: Spec[CachedValue[T]] = Spec { resolver =>
    CachedValue[T](resolver.in("updatedAt"), resolver.in("result"))
  }
}
