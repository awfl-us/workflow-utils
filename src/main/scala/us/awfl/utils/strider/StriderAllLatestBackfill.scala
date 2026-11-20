package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.utils.Yoj
import us.awfl.utils.Convo
import us.awfl.utils.Ista
import us.awfl.utils.Post
import us.awfl.utils.SegKala
import us.awfl.utils.Chainable

/**
 * Aggregation and orchestration traits: All, Latest, Backfill.
 */
trait All[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: us.awfl.utils.Sadhaka[Params]) extends us.awfl.core.Workflow { self =>
  import StriderObj.*

  override type Input = StriderInput[Params]
  override type Result = Out

  override val inputVal: Value[StriderInput[Params]] = init("input")

  def name: String

  def prompt: String

  protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]): List[Step[_, _]] = List()

  def run(kalaName: String): Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-${kalaName}$${WORKFLOW_ENV}"),
      obj(input),
      connector_params = ConnectorParams(false)
    )
    Call(
      s"${kalaName}_run",
      CallOperations.WorkflowRun,
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

  private lazy val convo   = new ConvoStrider[In, Params, Out]   { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val session = new SessionStrider[In, Params, Out] { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val week    = new WeekStrider[In, Params, Out]    { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val term    = new TermStrider[In, Params, Out]    { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }

  private def convoWf: List[Workflow[_]]   = convo.workflows.map(_.copy(name   = Some("Convo")))
  private def sessionWf: List[Workflow[_]] = session.workflows.map(_.copy(name = Some("Session")))
  private def weekWf: List[Workflow[_]]    = week.workflows.map(_.copy(name    = Some("Week")))
  private def termWf: List[Workflow[_]]    = term.workflows.map(_.copy(name    = Some("Term")))

  private def backfillWf: Workflow[Out]      = new Backfill[In, Params, Out](name, prompt) {
    override protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]) = All.this.childPostWriteSteps(sessionId, responseId, response, at)
  }.workflow

  override def workflows: List[Workflow[_]] =
    aggWorkflow +: (convoWf ++ sessionWf ++ weekWf ++ termWf) :+ backfillWf
}

trait Latest[In, Params, Out](val nameStr: String, windowSeconds: Int = us.awfl.utils.Segments.DefaultWindowSeconds, overlapSeconds: Int = us.awfl.utils.Segments.DefaultOverlapSeconds)(using spec: Spec[Out],
                                       yoj:      Yoj[In],
                                       ista:     Ista[Out],
                                       sadhaka: us.awfl.utils.Sadhaka[Params])
    extends All[In, Params, Out] with Chainable {

  import StriderObj.*

  override def name: String = nameStr

  def prompt: String

  protected def onPostWrite(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]): List[Step[_, _]] = List()

  override protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: Value[Out], at: Value[Double]): List[Step[_, _]] =
    onPostWrite(sessionId, responseId, response, at)

  private def latestEnd: Value[Double] = segment.flatMap(_.end)

  private def runAllLatest: Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput[Params](inputVal.flatMap(_.agentId), latestEnd)),
      connector_params = ConnectorParams(false)
    )
    Call(
      "All_Latest_run",
      CallOperations.WorkflowRun,
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
      )
    )
  }

  override def workflows: List[Workflow[_]] = super.workflows :+ latestWrapper

  def apply(stepName: String, agentId: Value[String]): Post[Nothing] = {
    val args = RunWorkflowArgs(
      str(s"${name}$${WORKFLOW_ENV}"),
      obj(StriderInput[Params](agentId)),
      connector_params = ConnectorParams(true)
    )
    Call(
      stepName,
      CallOperations.WorkflowRun,
      obj(args)
    )
  }
}

trait Backfill[In, Params, Out](val nameStr: String, val prompt: String)(using spec: Spec[Out],
                                          yoj:      Yoj[In],
                                          ista:     Ista[Out],
                                       sadhaka: us.awfl.utils.Sadhaka[Params])
    extends All[In, Params, Out] {

  import StriderObj.*

  override def name: String = nameStr

  private def runAllAt(endTime: Value[Double]): Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput[Params](inputVal.flatMap(_.agentId), endTime)),
      connector_params = ConnectorParams(false)
    )
    Call(
      "All_Backfill_run",
      CallOperations.WorkflowRun,
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

  // A caller has no Strider input scope: supply the agent target explicitly.
  // The historical implementation accidentally read input.agentId here.
  def apply(sessionId: Value[String], agentId: Value[String]): Post[Nothing] = {
    val args = RunWorkflowArgs(
      str(s"${name}-Backfill$${WORKFLOW_ENV}"),
      obj(StriderInput[Params](agentId)),
      connector_params = ConnectorParams(true)
    )
    Call(
      "RunBackfill",
      CallOperations.WorkflowRun,
      obj(args)
    )
  }
}