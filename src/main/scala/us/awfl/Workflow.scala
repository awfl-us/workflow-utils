package us.awfl.core

import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.{Workflow => DslWorkflow}
import us.awfl.utils.Exec
import us.awfl.utils.Env
import us.awfl.utils.ENV

case class WorkflowId[In, Out] private[core] (workflowName: Value[String])

trait Workflow {
  type Input
  type Result

  val inputVal: Value[Input]
  lazy val input = inputVal.get

  def workflows: List[DslWorkflow[?]] = List()

  def workflowName: String = {
    val name = this.getClass.getName
    val noPrefix = name.replaceFirst("""^.*\.?workflows\.?""", "")
    noPrefix.replace('.', '-').stripSuffix("$")
  }

  def buildSteps(
    main: (List[Step[?, ?]], BaseValue[Result]),
    except: Resolved[Error] => (List[Step[?, ?]], BaseValue[Result])
  )(using Spec[Result]): (List[Step[?, ?]], BaseValue[Result]) = {
    // Resolve execution IDs
    val triggeredExecId: Value[String] = Exec.currentExecId
    val callingExecId: Value[String] = Env.callingWorkflowExec.getOrElse(Value.nil)

    // 1) Register this execution under the session (idempotent create -> update on 409)
    val registerExecForSession = Exec.registerExecForSession(
      "registerExecForSession",
      execId = triggeredExecId,
    )

    // 2) If a caller exec id is provided, link (caller -> triggered) with idempotency
    val registerWorkflowExecLink = Exec.registerExecLink(
      "registerWorkflowExecLink",
      callingExecId = callingExecId,
      triggeredExecId = triggeredExecId,
    )

    val (steps, result) = main
    Try(
      "mainTry",
      (registerExecForSession :: registerWorkflowExecLink :: steps) -> result,
      { err =>
        val (exceptSteps, exceptResult) = except(err)
        (exceptSteps :+ Raise("ReRaise_error", err)) -> exceptResult
      }
    ).fn
  }

  def id(using Spec[Result]): WorkflowId[Input, Result] = WorkflowId(str(s"${workflowName}$${WORKFLOW_ENV}"))

  def execute[In, Out: Spec](wfName: String, input: BaseValue[In]): Call[RunWorkflowArgs[In], Out] = {
    val args = RunWorkflowArgs(str(s"${wfName}$${WORKFLOW_ENV}"), input)
    val cleanName = wfName.replace('-', '_')
    Call(cleanName, "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run", obj(args))
  }
}
