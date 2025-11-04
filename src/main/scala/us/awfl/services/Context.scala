package us.awfl.services

import io.circe.generic.auto._
import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.Cel._
import us.awfl.dsl.CelOps._
import Firebase.{ListItem, ListResult}
import us.awfl.utils.post
import us.awfl.utils.Env

// Context service: client for JS server-side Prakriya context endpoints
// Endpoints are mounted under /context (see functions/jobs/context/prakriyaContext.js)
object Context {

  // Kala payload with explicit kind field (the JS endpoint requires kala.kind)
  case class Kala(
    kind: BaseValue[String],
    sessionId: BaseValue[String] = Value("null"),
    end: BaseValue[Double] = Value("null"),
    windowSeconds: BaseValue[Double] = Value("null"),
    sessionEnd: BaseValue[Double] = Value("null"),
    weekEnd: BaseValue[Double] = Value("null"),
    begin: BaseValue[Double] = Value("null")
  )

  // Convenience constructors to build Kala payloads
  def segKala(sessionId: BaseValue[String], end: BaseValue[Double], windowSeconds: BaseValue[Double]): BaseValue[Kala] =
    obj(Kala(str("SegKala"), sessionId, end, windowSeconds))

  def sessionKala(sessionId: BaseValue[String], sessionEnd: BaseValue[Double]): BaseValue[Kala] =
    obj(Kala(str("SessionKala"), sessionId, sessionEnd = sessionEnd))

  def weekKala(weekEnd: BaseValue[Double]): BaseValue[Kala] =
    obj(Kala(str("WeekKala"), weekEnd = weekEnd))

  def termKala(begin: BaseValue[Double], end: BaseValue[Double]): BaseValue[Kala] =
    obj(Kala(str("TermKala"), begin = begin, end = end))

  // Request bodies
  case class YojReadArgs(name: BaseValue[String], kala: BaseValue[Kala], userId: Field = Env.userId)
  case class IstaReadArgs(name: BaseValue[String], kala: BaseValue[Kala], userId: Field = Env.userId)

  // POST /context/yoj/read -> { documents: [...] }
  def yojRead[T: Spec](name: String, yojName: BaseValue[String], kala: BaseValue[Kala]):
    Step[ListResult[T], BaseValue[ListResult[T]]] with ValueStep[ListResult[T]] = {
    post[YojReadArgs, ListResult[T]](name, "context/yoj/read", obj(YojReadArgs(yojName, kala))).flatMap(_.body)
  }

  // POST /context/ista/read -> { documents: [...] }
  def istaRead[T: Spec](name: String, istaName: BaseValue[String], kala: BaseValue[Kala]):
    Step[ListResult[T], BaseValue[ListResult[T]]] with ValueStep[ListResult[T]] = {
    post[IstaReadArgs, ListResult[T]](name, "context/ista/read", obj(IstaReadArgs(istaName, kala))).flatMap(_.body)
  }
}
