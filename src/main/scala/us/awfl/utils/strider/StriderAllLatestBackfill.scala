package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.utils.Yoj
import us.awfl.utils.Convo
import us.awfl.utils.Ista
import us.awfl.utils.Post
import us.awfl.utils.SegKala

/**
 * Aggregation and orchestration traits: All, Latest, Backfill.
 */
trait All[In, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], prompt: Convo.Prompt) extends us.awfl.core.Workflow {
  import StriderObj.*

  override type Input = StriderInput
  override type Result = Out

  override val inputVal: BaseValue[StriderInput] = StriderObj.inputVal

  def name: String

  protected def childEnablePostWrite: Boolean = false
  protected def childPostWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]): List[Step[_, _]] = List()

  def run(kalaName: String): Call[RunWorkflowArgs[StriderInput], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-${kalaName}$${WORKFLOW_ENV}"),
      obj(input),
      connector_params = ConnectorParams(false)
    )
    Call(
      s"${kalaName}_run",
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }

  private def runSegments = run("Convo")
  private def aggWorkflow: Workflow[Out] = {
    val result = runSegments.resultValue
    Workflow(
      (
        List[Step[_, _]](
          runSegments,
          run("Session"),
          run("Week"),
          run("Term"),
          Return("return", result)
        ),
        result
      ),
      name = Some("All")
    )
  }

  private lazy val convo   = new ConvoStrider[In, Out]   { override val name = name; override protected def enablePostWrite: Boolean = childEnablePostWrite; override protected def postWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]) = childPostWriteSteps(sessionId, responseId, at) }
  private lazy val session = new SessionStrider[In, Out] { override val name = name; override protected def enablePostWrite: Boolean = childEnablePostWrite; override protected def postWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]) = childPostWriteSteps(sessionId, responseId, at) }
  private lazy val week    = new WeekStrider[In, Out]    { override val name = name; override protected def enablePostWrite: Boolean = childEnablePostWrite; override protected def postWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]) = childPostWriteSteps(sessionId, responseId, at) }
  private lazy val term    = new TermStrider[In, Out]    { override val name = name; override protected def enablePostWrite: Boolean = childEnablePostWrite; override protected def postWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]) = childPostWriteSteps(sessionId, responseId, at) }

  private def convoWf: List[Workflow[_]]   = convo.workflows.map(_.copy(name   = Some("Convo")))
  private def sessionWf: List[Workflow[_]] = session.workflows.map(_.copy(name = Some("Session")))
  private def weekWf: List[Workflow[_]]    = week.workflows.map(_.copy(name    = Some("Week")))
  private def termWf: List[Workflow[_]]    = term.workflows.map(_.copy(name    = Some("Term")))

  private def backfillWf: Workflow[Out]      = new Backfill[In, Out](name) {
    override protected def childEnablePostWrite: Boolean = All.this.childEnablePostWrite
    override protected def childPostWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]) = All.this.childPostWriteSteps(sessionId, responseId, at)
  }.workflow

  override def workflows: List[Workflow[_]] =
    aggWorkflow +: (convoWf ++ sessionWf ++ weekWf ++ termWf) :+ backfillWf
}

trait Latest[In, Out](val nameStr: String, windowSeconds: Int = us.awfl.utils.Segments.DefaultWindowSeconds, overlapSeconds: Int = us.awfl.utils.Segments.DefaultOverlapSeconds)(using spec: Spec[Out],
                                       yoj:      Yoj[In],
                                       ista:     Ista[Out],
                                       prompt:   Convo.Prompt)
    extends All[In, Out] {

  import StriderObj.*

  override def name: String = nameStr

  protected def postWriteEnabled: Boolean = false
  protected def onPostWrite(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]): List[Step[_, _]] = List()

  override protected def childEnablePostWrite: Boolean = postWriteEnabled
  override protected def childPostWriteSteps(sessionId: Value[String], responseId: BaseValue[String], at: BaseValue[Double]): List[Step[_, _]] =
    onPostWrite(sessionId, responseId, at)

  private def latestEnd: BaseValue[Double] = segment.flatMap(_.end)

  private def runAllLatest: Call[RunWorkflowArgs[StriderInput], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput(input.sessionId, latestEnd)),
      connector_params = ConnectorParams(false)
    )
    Call(
      "All_Latest_run",
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }

  private def latestWrapper: Workflow[Out] = {
    val result = runAllLatest.resultValue
    Workflow(
      (
        List[Step[_, _]](
          segments,
          runAllLatest,
          Return("returnLatest", result)
        ),
        result
      ),
      name = Some("All-Latest-wrapper")
    )
  }

  override def workflows: List[Workflow[_]] = super.workflows :+ latestWrapper

  def apply(stepName: String, sessionId: Value[String]): Post[Nothing] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All-Latest-wrapper$${WORKFLOW_ENV}"),
      obj(StriderInput(sessionId, Value("null"))),
      connector_params = ConnectorParams(true)
    )
    Call(
      stepName,
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }
}

trait Backfill[In, Out](val nameStr: String)(using spec: Spec[Out],
                                          yoj:      Yoj[In],
                                          ista:     Ista[Out],
                                          prompt:   Convo.Prompt)
    extends All[In, Out] {

  import StriderObj.*

  override def name: String = nameStr

  private def runAllAt(endTime: BaseValue[Double]): Call[RunWorkflowArgs[StriderInput], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput(input.sessionId, endTime)),
      connector_params = ConnectorParams(false)
    )
    Call(
      "All_Backfill_run",
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }

  private val forEachSegment = For[SegKala, Out]("forEachSegment", segments.resultValue) { seg =>
    runAllAt(seg.flatMap(_.end)).fn
  }

  val workflow: Workflow[Out] = {
    val result = forEachSegment.resultValue
    Workflow(
      (
        List[Step[_, _]](
          segments,
          forEachSegment,
          Return("returnBackfill", result)
        ),
        result
      ),
      name = Some("Backfill")
    )
  }

  def apply(sessionId: Value[String]): Post[Nothing] = {
    val args = RunWorkflowArgs(
      str(s"${name}-Backfill$${WORKFLOW_ENV}"),
      obj(StriderInput(sessionId, Value("null"))),
      connector_params = ConnectorParams(true)
    )
    Call(
      "RunBackfill",
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }
}