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
import us.awfl.utils.Ista
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Timestamped
import us.awfl.utils.SegKala

/**
 * Base Strider implementation with optional post-write hook for SegKala.
 */
trait Strider[In, Out](using yoj: Yoj[In], ista: Ista[Out], spec: Spec[Out], prompt: Convo.Prompt) extends us.awfl.core.Workflow {
  import StriderObj.*

  type Input = StriderInput
  type Result = Out

  override val inputVal = StriderObj.inputVal

  def name: String

  def kala: KalaVibhaga
  given KalaVibhaga = kala

  val yojWorkflowName = s"${Yoj.kalaName}-Yoj"

  protected def enablePostWrite: Boolean = false
  protected def postWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]): List[Step[_, _]] = List()

  val strideStep: Step[Out, BaseValue[Out]] = if (enablePostWrite) {
    Convo.inSession[Out]("inSession", StriderObj.input.sessionId) {
      val key = Locks.key(ista.name)
      val owner: Value[String] = Value("uuid.generate()")
      val acquired = Locks.acquireBool("lock_acquire", key, owner, 300)

      val messagesInWindow = Try("messagesInWindow", summon[Yoj[ChatMessage]].build(using StriderObj.segmentKala).fn)
      val check            = ista.read[Out]

      val shouldWrite: BaseValue[Boolean] = Value((len(messagesInWindow.resultValue) > 0) && (
        ((len(check.resultValue) === 0) || (check.resultValue(len(check.resultValue) - 1).flatMap(_.create_time).cel < StriderObj.segmentKala.end))
      ))

      val buildYoj = yoj.build
      val complete = Convo.complete[In, Out](
        "complete",
        prompt,
        buildYoj.resultValue
      )

      val doWriteSeg: Step[Out, BaseValue[Out]] = {
        val newId        = Try[String, Value[String]]("ista_write_newId", List() -> Value("uuid.generate()"))
        val payload      = obj(us.awfl.utils.Timestamped(complete.result.result, StriderObj.segmentKala.end, complete.result.total_cost))
        val createChild  = create("ista_write_create_child", ista.convoCollection(StriderObj.input.sessionId), newId.resultValue, payload)
        val updateParent = update(
          "ista_write_update_parent_update_time",
          str("convo.sessions": Cel),
          StriderObj.input.sessionId,
          obj(Map("update_time" -> StriderObj.segmentKala.end))
        )
        val post = Block(
          "post_write_block",
          postWriteSteps(StriderObj.input.sessionId, newId.resultValue, StriderObj.segmentKala.end) -> newId.resultValue
        )
        Block(
          "do_write_seg_block",
          List[Step[_, _]](newId, createChild, updateParent, post) -> complete.resultValue.flatMap(_.result)
        )
      }

      val doWriteOther: Step[Out, BaseValue[Out]] = {
        val write = ista.write("writeIstaResult", complete.result.result, StriderObj.segmentKala.end, complete.result.total_cost)
        Block("do_write_other_block", List[Step[_, _]](write) -> complete.resultValue.flatMap(_.result))
      }

      val doWrite = summon[KalaVibhaga] match {
        case _: SegKala => doWriteSeg
        case _          => doWriteOther
      }

      val workSwitch = Switch(
        "ista_write_switch",
        List(
          (shouldWrite.cel === Value(true)) -> (List[Step[_, _]](buildYoj, complete, doWrite) -> doWrite.resultValue),
          (true: Cel) -> (List(Log("write_skipped", str("Summary already exists or no messages. Skipping write."))) -> Value("null"))
        )
      )

      val release = Locks.release("lock_release", key, owner)

      val runIfAcquired = Switch("lock_switch", List(
        (acquired.resultValue === Value(true)) -> {
          val workBlock = Block("work_block", List[Step[_, _]](messagesInWindow, check, workSwitch) -> workSwitch.resultValue)
          List(workBlock, release) -> workBlock.resultValue
        },
        (true: Cel) -> (List() -> Value("null"))
      ))

      List[Step[_, _]](acquired, runIfAcquired) -> runIfAcquired.resultValue
    }
  } else {
    Convo.inSession[Out]("inSession", StriderObj.input.sessionId) {
      val key = Locks.key(ista.name)
      val owner: Value[String] = Value("uuid.generate()")
      val acquired = Locks.acquireBool("lock_acquire", key, owner, 300)

      val messagesInWindow = Try("messagesInWindow", summon[Yoj[ChatMessage]].build(using StriderObj.segmentKala).fn)

      val buildYoj = TopicContextYoj.run(includeDocId = true)

      val complete = Convo.complete[In, Out](
        "complete",
        prompt,
        buildYoj.resultValue.flatMap(_.body).flatMap(_.yoj)
      )

      val write  = ista.write("writeIstaResult", complete.result.result, StriderObj.segmentKala.end, complete.result.total_cost)
      val check  = ista.read[Out]

      val workSwitch = Switch(
        "ista_write_switch",
        List(
          ((len(messagesInWindow.resultValue) > 0) && (((len(check.resultValue) === 0) ||
            (check.resultValue(len(check.resultValue) - 1).flatMap(_.create_time).cel < StriderObj.segmentKala.end)))) ->
              (List[Step[_, _]](buildYoj, complete, write) -> complete.resultValue.flatMap(_.result)),
          (true: Cel) ->
            (List(Log("write_skipped", str("Summary already exists or no messages. Skipping write."))) -> Value("null"))
        )
      )

      val release = Locks.release("lock_release", key, owner)

      val runIfAcquired = Switch("lock_switch", List(
        (acquired.resultValue === Value(true)) -> {
          val workBlock = Block("work_block", List[Step[_, _]](messagesInWindow, check, workSwitch) -> workSwitch.resultValue)
          List(workBlock, release) -> workBlock.resultValue
        },
        (true: Cel) -> (List() -> Value("null"))
      ))

      List[Step[_, _]](acquired, runIfAcquired) -> runIfAcquired.resultValue
    }
  }

  override def workflows = List(
    Workflow(strideStep.fn, name = Some(Yoj.kalaName))
  )
}
