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
import us.awfl.utils.Sadhaka
import us.awfl.core.WorkflowId
import us.awfl.services.Llm.ChatResponse
import us.awfl.ista.ChatMessage

/**
 * Aggregation and orchestration traits: All, Latest, Backfill.
 */
trait All[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: Sadhaka[Params]) extends us.awfl.core.Workflow { self =>
  import StriderObj.*

  override type Input = StriderInput[Params]
  override type Result = Out

  override val inputVal: Value[StriderInput[Params]] = init("input")

  def name: String

  def prompt: String

  protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]): List[Step[?, ?]] = List()

  def run(kalaName: String): Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
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
        List[Step[?, ?]](
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

  private lazy val convo   = new ConvoStrider[In, Params, Out]   { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val session = new SessionStrider[In, Params, Out] { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val week    = new WeekStrider[In, Params, Out]    { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }
  private lazy val term    = new TermStrider[In, Params, Out]    { override val name = name; override def prompt = self.prompt; override protected def postWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]) = childPostWriteSteps(sessionId, responseId, response, at) }

  private def convoWf: List[Workflow[?]]   = convo.workflows.map(_.copy(name   = Some("Convo")))
  private def sessionWf: List[Workflow[?]] = session.workflows.map(_.copy(name = Some("Session")))
  private def weekWf: List[Workflow[?]]    = week.workflows.map(_.copy(name    = Some("Week")))
  private def termWf: List[Workflow[?]]    = term.workflows.map(_.copy(name    = Some("Term")))

  private def backfillWf: Workflow[Out]      = new Backfill[In, Params, Out](name, prompt) {
    override protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]) = All.this.childPostWriteSteps(sessionId, responseId, response, at)
  }.workflow

  override def workflows: List[Workflow[?]] =
    aggWorkflow +: (convoWf ++ sessionWf ++ weekWf ++ termWf) :+ backfillWf
}

trait Latest[In, Params, Out](val nameStr: String, windowSeconds: Int = us.awfl.utils.Segments.DefaultWindowSeconds, overlapSeconds: Int = us.awfl.utils.Segments.DefaultOverlapSeconds)(using spec: Spec[Out],
                                       yoj:      Yoj[In],
                                       ista:     Ista[Out])
    extends All[In, Params, Out] with Chainable {

  import StriderObj.*

  override def name: String = nameStr

  def prompt: String

  protected def onPostWrite(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]): List[Step[?, ?]] = List()

  override protected def childPostWriteSteps(sessionId: Value[String], responseId: Value[String], response: BaseValue[Out], at: Value[Double]): List[Step[?, ?]] =
    onPostWrite(sessionId, responseId, response, at)

  private def latestEnd: Value[Double] = segment.flatMap(_.end)

  private def runAllLatest: Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput(input.agentId, latestEnd)),
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
        List[Step[?, ?]](
          segments,
          runAllLatest,
          Return("returnLatest", result)
        ),
        result
      )
    )
  }

  override def workflows: List[Workflow[?]] = super.workflows :+ latestWrapper

  def apply(agentId: WorkflowId[Params, ChatResponse[ChatMessage]]): Post[Nothing] = {
    val args = RunWorkflowArgs(
      str(s"${name}$${WORKFLOW_ENV}"),
      obj(StriderInput(agentId)),
      connector_params = ConnectorParams(true)
    )
    Call(
      nameStr,
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }
}

trait Backfill[In, Params, Out](val nameStr: String, val prompt: String)(using spec: Spec[Out],
                                          yoj:      Yoj[In],
                                          ista:     Ista[Out])
    extends All[In, Params, Out] {

  import StriderObj.*

  override def name: String = nameStr

  private def runAllAt(endTime: Value[Double]): Call[RunWorkflowArgs[StriderInput[Params]], Out] = {
    val args = RunWorkflowArgs(
      str(s"${name}-All$${WORKFLOW_ENV}"),
      obj(StriderInput(input.agentId, endTime)),
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
        List[Step[?, ?]](
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
      obj(StriderInput(input.agentId)),
      connector_params = ConnectorParams(true)
    )
    Call(
      "RunBackfill",
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run",
      obj(args)
    )
  }
}