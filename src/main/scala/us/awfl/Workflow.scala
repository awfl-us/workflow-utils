package us.awfl.core

import us.awfl.dsl
import us.awfl.dsl.auto.given
import us.awfl.dsl.stringSpec
import us.awfl.utils.Exec
import us.awfl.utils.Env

trait Workflow {
  type Input
  type Result

  val inputVal: dsl.BaseValue[Input]
  lazy val input = inputVal.get

  // lazy val sessionId = dsl.Value[String](dsl.CelFunc("map.get", inputVal.cel, "sessionId"))

  def workflows: List[dsl.Workflow[_]] = List()

  def workflowName: String = {
    val name = this.getClass.getName
    val noPrefix = name.replaceFirst("""^.*\.?workflows\.?""", "")
    noPrefix.replace('.', '-').stripSuffix("$")
  }

  def buildSteps[T: dsl.Spec](
    main: (List[dsl.Step[_, _]], dsl.BaseValue[T]),
    except: dsl.Resolved[dsl.Error] => (List[dsl.Step[_, _]], dsl.BaseValue[T])
  ): (List[dsl.Step[_, _]], dsl.BaseValue[T]) = {
    // Resolve execution IDs
    val triggeredExecId: dsl.Value[String] = Exec.currentExecId
    val callingExecId: dsl.BaseValue[String] = Env.callingWorkflowExec.getOrElse(dsl.Value.nil)

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
    dsl.Try(
      "mainTry",
      (registerExecForSession :: registerWorkflowExecLink :: steps) -> result,
      { err =>
        val (exceptSteps, exceptResult) = except(err)
        (exceptSteps :+ dsl.Raise("ReRaise_error", err)) -> exceptResult
      }
    ).fn
  }

  def execute[In, Out: dsl.Spec](wfName: String, input: dsl.BaseValue[In]): dsl.Call[dsl.RunWorkflowArgs[In], Out] = {
    val args = dsl.RunWorkflowArgs(dsl.str(s"${wfName}$${WORKFLOW_ENV}"), input)
    val cleanName = wfName.replace('-', '_')
    dsl.Call(cleanName, "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run", dsl.obj(args))
  }
}