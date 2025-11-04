package us.awfl.services

import io.circe.Encoder
import io.circe.generic.auto._
import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.Cel._
import us.awfl.dsl.CelOps._
import io.circe.Json
import us.awfl.utils._

object Firebase {

  // Include userId in request bodies so it passes through by default
  case class UpdateArgs[T](
    collection: BaseValue[String],
    id: BaseValue[_],
    contents: BaseValue[T],
    userId: Field = Env.userId
  )
  case class RefArgs(
    collection: BaseValue[String],
    id: Value[String],
    userId: Field = Env.userId
  )

  // Create: fails if document exists
  def create[T](
    name: String,
    collection: BaseValue[String],
    id: BaseValue[_],
    contents: BaseValue[T]
  ): Post[Nothing] = {
    val body = UpdateArgs(collection, id, contents)
    post[UpdateArgs[T], Nothing](name, "firebase/create", obj(body))
  }

  // Update: fails if document is missing
  def update[T](
    name: String,
    collection: BaseValue[String],
    id: BaseValue[_],
    contents: BaseValue[T]
  ): Post[Nothing] = {
    val body = UpdateArgs(collection, id, contents)
    post[UpdateArgs[T], Nothing](name, "firebase/update", obj(body), Auth())
  }

  // Read: returns document data + subcollections
  def read[T: Spec](
    name: String,
    collection: BaseValue[String],
    id: Value[String]
  ): Try[PostResult[T], BaseValue[PostResult[T]]] = {
  
    val postStep: Post[T] = {
      val body = RefArgs(collection, id)
      post[RefArgs, T](name, "firebase/read", obj(body), Auth())
    }

    Try(
      s"${name}_tryRead",
      List(postStep) -> postStep.resultValue,
      err => Switch(s"${name}_switchRead", List(
        (err.get.code.cel === 404) -> (List(Log(s"${name}_log404", obj("404 - File not found"))) -> postStep.resultValue),
        (true: Cel) -> (List(Raise(s"${name}_rethrow", err)) -> postStep.resultValue)
      )).fn
    )
  }

  // Delete: deletes a document
  def delete(
    name: String,
    collection: Value[String],
    id: Value[String]
  ): Post[NoValueT] = {
    val body = RefArgs(collection, id)
    post[RefArgs, NoValueT](name, "firebase/delete", obj(body), Auth())
  }

  case class ListArgs(
    collection: BaseValue[String],
    at: BaseValue[Double],
    before: Option[Int],
    after: Option[Int],
    userId: Field = Env.userId
  )
  case class ListBetweenArgs(
    collection: BaseValue[String],
    start: BaseValue[Double],
    end: BaseValue[Double],
    userId: Field = Env.userId
  )
  case class ListItem[T](id: Field, data: BaseValue[T])
  case class ListResult[T](documents: ListValue[ListItem[T]])

  def listAt[T: Spec](
    name: String,
    collection: BaseValue[String],
    at: BaseValue[Double],
    before: Option[Int],
    after: Option[Int]
  ): Step[ListResult[T], BaseValue[ListResult[T]]] with ValueStep[ListResult[T]] = {
    post[ListArgs, ListResult[T]](name, "firebase/listAt", obj(ListArgs(collection, at, before, after)), Auth()).flatMap(_.body)
  }

  def list[T: Spec](
    name: String,
    collection: BaseValue[String],
    start: BaseValue[Double],
    end: BaseValue[Double]
  ): Step[ListResult[T], BaseValue[ListResult[T]]] with ValueStep[ListResult[T]] = {
    post[ListBetweenArgs, ListResult[T]](name, "firebase/list", obj(ListBetweenArgs(collection, start, end)), Auth()).flatMap(_.body)
  }

  // ---------------------------------------------------------------------------
  // NEW: segmentsForSession helper – fetch populated SegKala windows only
  // ---------------------------------------------------------------------------
  case class SegmentsArgs(
    collection: BaseValue[String],
    windowSeconds: Value[Double],
    overlapSeconds: Value[Int],
    userId: Field = Env.userId
  )
  case class Segment(end: BaseValue[Double], windowSeconds: BaseValue[Double])
  case class SegmentsResult(segments: ListValue[Segment])

  def segmentsForSession(
    name: String,
    collection: BaseValue[String],
    windowSeconds: Value[Double] = Value(1200),
    overlapSeconds: Value[Int] = Value(240)
  ): Step[SegmentsResult, BaseValue[SegmentsResult]] with ValueStep[SegmentsResult] = {
    post[SegmentsArgs, SegmentsResult](
      name,
      "firebase/segmentsForSession",
      obj(SegmentsArgs(collection, windowSeconds, overlapSeconds)),
      Auth()
    ).flatMap(_.body)
  }

  // ---------------------------------------------------------------------------
  // NEW: lock acquire/release with TTL
  // ---------------------------------------------------------------------------
  case class LockAcquireArgs(
    collection: BaseValue[String],
    id: Value[String],
    ttlSeconds: Int,
    owner: Value[String],
    userId: Field = Env.userId
  )
  case class LockResult(acquired: BaseValue[Boolean], owner: Value[String], created: BaseValue[Double])
  case class LockReleaseArgs(
    collection: BaseValue[String],
    id: Value[String],
    owner: Value[String],
    userId: Field = Env.userId
  )

  def acquireLock(
    name: String,
    collection: BaseValue[String],
    id: Value[String],
    owner: Value[String],
    ttlSeconds: Int = 300
  ): Step[LockResult, BaseValue[LockResult]] with ValueStep[LockResult] = {
    val body = LockAcquireArgs(collection, id, ttlSeconds, owner)
    post[LockAcquireArgs, LockResult](name, "firebase/locks/acquire", obj(body), Auth()).flatMap(_.body)
  }

  def releaseLock(
    name: String,
    collection: BaseValue[String],
    id: Value[String],
    owner: Value[String]
  ): Post[NoValueT] = {
    val body = LockReleaseArgs(collection, id, owner)
    post[LockReleaseArgs, NoValueT](name, "firebase/locks/release", obj(body), Auth())
  }
}
