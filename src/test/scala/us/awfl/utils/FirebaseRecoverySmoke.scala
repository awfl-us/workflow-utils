package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.auto.given
import us.awfl.services.Firebase

object FirebaseRecoverySmoke {
  case class Summary(title: Value[String])

  def main(args: Array[String]): Unit = {
    val read = Firebase.read[Summary]("cacheRead", str("collection"), str("id"))
    val recovered = read.except._1.head.asInstanceOf[Switch[?, ?]]
    val missing = recovered.cases.head._2._2
    assert(missing.isInstanceOf[Value[?]])
    assert(missing.asInstanceOf[Value[?]].resolver.path.path == List(CelConst("null")))
    println("FIREBASE_RECOVERY_SMOKE_PASS")
  }
}
