package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.utils.Yoj
import us.awfl.utils.Convo
import us.awfl.utils.Locks
import us.awfl.utils.Segments
import us.awfl.utils.TopicContextYoj
import us.awfl.services.Firebase.create
import us.awfl.services.Firebase.update
import us.awfl.utils.Sadhaka
import us.awfl.utils.Ista
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Timestamped
import us.awfl.utils.SegKala
import us.awfl.utils.Env

/**
 * Base Strider implementation with optional post-write hook for SegKala.
 */
trait Strider[In, Params, Out](using yoj: Yoj[In], ista: Ista[Out], spec: Spec[Out], sadhaka: Sadhaka[Params]) extends us.awfl.core.Workflow {
  import StriderObj.*

  type Input = StriderInput[Params]
  type Result = Out

  override val inputVal = init[StriderInput[Params]]("input")

  def name: String

  def prompt: String

  def kala: KalaVibhaga
  given KalaVibhaga = kala

  val yojWorkflowName = s"${Yoj.kalaName}-Yoj"

  protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]): List[Step[_, _]] = List()

  val strideStep = Convo.inSession[Out]("inSession", Env.sessionId) {
    val key = Locks.key(ista.name)
    // Value(uuid.generate()) is an expression, not a memoized value: passing
    // it to both HTTP effects evaluates two different UUIDs. Materialize it
    // once so an owner-scoped release can actually delete the acquired lock.
    val ownerStep = Try("lock_owner", List() -> Value[String](uuid.generate()))
    val owner: Value[String] = ownerStep.resultValue
    val acquired = Locks.acquireBool("lock_acquire", key, owner, 300)

    val messagesInWindow = summon[Yoj[ChatMessage]].build(using StriderObj.segmentKala)
    val check            = ista.read[Out]

    val shouldWrite: Value[Boolean] = Value((len(messagesInWindow.resultValue) > 0) && (
      ((len(check.resultValue) === 0) || (check.resultValue(len(check.resultValue) - 1).flatMap(_.create_time).cel < StriderObj.segmentKala.end))
    ))

    val complete = sadhaka.complete[In, Out](prompt, inputVal.flatMap(_.agentId))

    val doWriteSeg: Step[Out, BaseValue[Out]] = {
      val newId        = Try("ista_write_newId", List() -> Value[String](uuid.generate()))
      val payload      = obj(us.awfl.utils.Timestamped(complete.result.message, StriderObj.segmentKala.end, complete.result.total_cost))
      val createChild  = create("ista_write_create_child", ista.convoCollection(Env.sessionId), newId.resultValue, payload)
      val updateParent = update(
        "ista_write_update_parent_update_time",
        str("convo.sessions": Cel),
        Env.sessionId,
        obj(Map("update_time" -> StriderObj.segmentKala.end))
      )
      val post = Block(
        "post_write_block",
        postWriteSteps(Env.sessionId, newId.resultValue, complete.result.message, StriderObj.segmentKala.end) -> newId.resultValue
      )
      Block(
        "do_write_seg_block",
        List[Step[_, _]](newId, createChild, updateParent, post) -> complete.resultValue.flatMap(_.message)
      )
    }

    val doWriteOther: Step[Out, BaseValue[Out]] = {
      val write = ista.write("writeIstaResult", complete.result.message, StriderObj.segmentKala.end, complete.result.total_cost)
      Block("do_write_other_block", List[Step[_, _]](write) -> complete.resultValue.flatMap(_.message))
    }

    val doWrite = summon[KalaVibhaga] match {
      case _: SegKala => doWriteSeg
      case _          => doWriteOther
    }

    val workSwitch = Switch(
      "ista_write_switch",
      List(
        shouldWrite.cel -> (List[Step[_, _]](complete, doWrite) -> doWrite.resultValue),
        (true: Cel) -> (List(Log("write_skipped", str("Summary already exists or no messages. Skipping write."))) -> Value("null"))
      )
    )

    val release = Locks.release("lock_release", key, owner)

    val runIfAcquired = Switch("lock_switch", List(
      (acquired.resultValue === Value(true)) -> {
        val workBlock = Block("work_block", List[Step[_, _]](messagesInWindow, check, workSwitch) -> workSwitch.resultValue)
        // Release our owner-scoped lock even when context construction,
        // typed Sadhaka completion, or the Ista write fails. Otherwise the
        // next Strider run for the segment waits for the five-minute TTL.
        val workAndRelease = Try(
          "strider_work_and_release",
          List[Step[_, _]](workBlock, release) -> workBlock.resultValue,
          err => List[Step[_, _]](
            Locks.release("lock_release_failure", key, owner),
            Raise("strider_work_rethrow", err)
          ) -> Value.nil[Out]
        )
        List[Step[_, _]](workAndRelease) -> workAndRelease.resultValue
      },
      (true: Cel) -> (List() -> Value("null"))
    ))

    List[Step[_, _]](ownerStep, acquired, runIfAcquired) -> runIfAcquired.resultValue
  }

  override def workflows = List(
    Workflow(strideStep.fn, name = Some(Yoj.kalaName))
  )
}
