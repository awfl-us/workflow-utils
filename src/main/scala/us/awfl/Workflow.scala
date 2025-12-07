package us.awfl.core

import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.{Workflow => DslWorkflow}
import us.awfl.utils.Exec
import us.awfl.utils.Env
import us.awfl.utils.ENV

trait Workflow {
  type Input
  type Result

  val inputVal: Value[Input]
  lazy val input = inputVal.get

  def workflows: List[DslWorkflow[_]] = List()

  def workflowName: String = {
    val name = this.getClass.getName
    val noPrefix = name.replaceFirst("""^.*\.?workflows\.?""", "")
    noPrefix.replace('.', '-').stripSuffix("$")
  }

  def buildSteps[T: Spec](
    main: (List[Step[_, _]], BaseValue[T]),
    except: Resolved[Error] => (List[Step[_, _]], BaseValue[T])
  ): (List[Step[_, _]], BaseValue[T]) = {
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

  def execute[In, Out: Spec](wfName: String, input: BaseValue[In]): Call[RunWorkflowArgs[In], Out] = {
    val args = RunWorkflowArgs(str(s"${wfName}$${WORKFLOW_ENV}"), input)
    val cleanName = wfName.replace('-', '_')
    Call(cleanName, "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run", obj(args))
  }
}
