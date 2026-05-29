package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given

case class Callback[T](id: Value[String]) {
  def apply[T](response: BaseValue[T]): Step[Boolean, Value[Boolean]] = {
    postV[T, Boolean](
      "callback",
      str(("callbacks/": Cel) + id),
      response
    ).flatMap(_.body)
  }

  def failure(error: BaseValue[Error]): Step[Boolean, Value[Boolean]] = {
    postV[Map[String, BaseValue[Error]], Boolean](
      "callback",
      str(("callbacks/": Cel) + id),
      obj(Map("error" -> error))
    ).flatMap(_.body)
  }
}

object Callback {
  // Workflows callback helpers
  case class CreateCallbackArgs(http_callback_method: BaseValue[String])
  case class CallbackDetails(url: BaseValue[String])
  case class AwaitCallbackArgs(callback: BaseValue[CallbackDetails], timeout: BaseValue[Int])

  case class CallbackRequest[T](http_request: BaseValue[PostRequest[T]])

  // jobs/callbacks service payloads
  case class CreateJobsCallbackBody(callback_url: BaseValue[String])

  def mkCreateCallback = Call[CreateCallbackArgs, CallbackDetails](
    s"createCallback",
    "events.create_callback_endpoint",
    obj(CreateCallbackArgs(str("POST")))
  )

  // Save the callback on our server to receive a callback ID
  // POST /jobs/callbacks with { callback_url }
  def mkSaveCallback[T](details: Value[CallbackDetails]) = post[CreateJobsCallbackBody, Callback[T]](
    "saveCallback",
    "callbacks",
    obj(CreateJobsCallbackBody(details.flatMap(_.url)))
  ).flatMap(_.body)

  def init[T: Spec]: Step[Callback[T], Value[Callback[T]]] = {
    val createCallback = mkCreateCallback
    val saveCallback = mkSaveCallback[T](createCallback.resultValue)
    Block("initCallback", List[Step[?,?]](createCallback, saveCallback) -> saveCallback.resultValue)
  }

  def apply[T: Spec](run: Callback[T] => Step[?, ?]): Step[T, Value[T]] = {
    val createCallback = mkCreateCallback
    val saveCallback = mkSaveCallback[T](createCallback.resultValue)
    val runStep = run(saveCallback.result)
    val awaitCallback = Call[AwaitCallbackArgs, CallbackRequest[T]](
      s"awaitCallback",
      "events.await_callback",
      obj(AwaitCallbackArgs(createCallback.resultValue, obj(3600)))
    )
    val body = awaitCallback.resultValue.flatMap(_.http_request).flatMap(_.body)
    val checkError = Switch("checkError", List(
      (("error" in body) && (CelFunc("get_type", CelFunc("map.get", body, "error")) === "map")) ->
        (List(Raise("raise", Value[Error](CelFunc("map.get", body, "error")))) -> Value.nil),
      (true: Cel) -> (List() -> body)
    ))

    Try(
      "createCallback_try",
      List[Step[?,?]](createCallback, saveCallback, runStep, awaitCallback, checkError) -> body,
      reRaise = true
    )
  }
}