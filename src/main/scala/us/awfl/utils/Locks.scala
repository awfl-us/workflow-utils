package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.services.Firebase

object Locks {
  case class Key(collection: Value[String], id: Value[String])

  // Build a normalized lock key: single collection per family+kala, with
  // document IDs encoding session + end when applicable to avoid creating
  // one collection per session. Structure examples:
  //  - locks.{name}.SegKala   / docId = "{sessionId}:{end}"
  //  - locks.{name}.SessionKala / docId = "{sessionId}:{end}"
  //  - locks.{name}.WeekKala  / docId = "{end}"
  //  - locks.{name}.TermKala  / docId = "{end}"
  def key(familyName: String)(using k: KalaVibhaga): Key = {
    val base = s"locks.${familyName}.${Yoj.kalaName}"
    val collection = str(base)
    val endStr: Value[String] = Value(CelFunc("string", k.end.cel))
    k match {
      case SegKala(sessionId, _, _) =>
        Key(collection, str(sessionId.cel + (":": Cel) + endStr.cel))
      case SessionKala(sessionId, _) =>
        Key(collection, str(sessionId.cel + (":": Cel) + endStr.cel))
      case WeekKala(_) =>
        Key(collection, endStr)
      case TermKala(_, _) =>
        Key(collection, endStr)
    }
  }

  // Session-scoped lock: use a single document per session, independent of timestamp.
  // This prevents parallel requests in the same session from running concurrent
  // "complete" operations when they are close in time but cross a segment boundary.
  // Structure:
  //  - Collection: locks.{familyName}.Session
  //  - Doc ID: {sessionId}
  def sessionKey(familyName: String)(using k: KalaVibhaga): Key = {
    val collection = str(s"locks.${familyName}.Session")
    val id: Value[String] = k match {
      case SegKala(sessionId, _, _) => sessionId
      case SessionKala(sessionId, _) => sessionId
      // For non-session contexts, fall back to a stable key to avoid NPEs; ideally
      // callers of sessionKey should provide a session-bearing KalaVibhaga.
      case WeekKala(_) => str("global")
      case TermKala(_, _) => str("global")
    }
    Key(collection, id)
  }

  // Acquire the lock with TTL; return acquired=true/false, mapping 409 to false.
  def acquireBool(stepName: String, key: Key, owner: Value[String], ttlSeconds: Int = 300): Try[Boolean, BaseValue[Boolean]] = {
    val acquire = Firebase.acquireLock(stepName, key.collection, key.id, owner, ttlSeconds)
    Try(
      s"${stepName}_try",
      List(acquire) -> acquire.resultValue.flatMap(_.acquired),
      err => Switch(s"${stepName}_err", List(
        (("code" in err.cel) && (err.get.code.cel === 409)) -> (List(Log(s"${stepName}_busy", str("Lock exists, skipping work."))) -> Value[Boolean](false)),
        (true: Cel) -> (List(Raise(s"${stepName}_rethrow", err)) -> Value[Boolean](false))
      )).fn
    )
  }

  // Release helper (owner-scoped)
  def release(stepName: String, key: Key, owner: Value[String]) =
    Firebase.releaseLock(stepName, key.collection, key.id, owner)
}
