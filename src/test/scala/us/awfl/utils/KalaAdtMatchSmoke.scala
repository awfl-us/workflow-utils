package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.auto.given

/** Concrete wip-old usage sites, not duplicate miniature Kala fixtures.
  * The native compiler's companion integration suite executes equivalent
  * serialized inputs and effect/resumption cases.
  */
object KalaAdtMatchSmoke:
  def main(args: Array[String]): Unit =
    val value = Value[KalaVibhaga]("input")

    val lock = Locks.buildSessionKey("unit", value)
    assert(lock.name == "sessionKeyTry")
    val lockMatch = lock.asInstanceOf[Try[Locks.Key, Value[Locks.Key]]].run._1.head.asInstanceOf[Block[String, Value[String]]]
    assert(lockMatch.name == "matchId_block")
    assert(lockMatch.steps.head.isInstanceOf[ForRange[?]])
    val lockSwitch = lockMatch.steps(1).asInstanceOf[Switch[String, Value[String]]]
    assert(lockSwitch.cases.size == 5)
    assert(lockSwitch.cases.last._2._1.head.isInstanceOf[Raise])
    assert(lockSwitch.cases.head._1.toString.contains("json.encode_to_string"))

    val context = TopicContextYoj.encodeKalaVal(value)
    assert(context.name == "encodeKalaVal_block")
    val contextMatch = context.asInstanceOf[Block[us.awfl.services.Context.Kala, Value[us.awfl.services.Context.Kala]]]
    assert(contextMatch.steps.head.isInstanceOf[ForRange[?]])
    val contextSwitch = contextMatch.steps(1).asInstanceOf[
      Switch[us.awfl.services.Context.Kala, Value[us.awfl.services.Context.Kala]]]
    assert(contextSwitch.cases.size == 5)
    assert(contextSwitch.cases.last._2._1.head.isInstanceOf[Raise])
    println("UTILS_KALA_ADT_MATCH_PASS")
