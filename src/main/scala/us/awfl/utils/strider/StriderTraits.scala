package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.utils.Yoj
import us.awfl.utils.Convo
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Ista
import us.awfl.utils.Convo.SessionId
import us.awfl.utils.Post
import us.awfl.utils.Segments
import us.awfl.utils.Chainable
import us.awfl.utils.Env

trait ConvoStrider[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: us.awfl.utils.Sadhaka[Params])
    extends Strider[In, Params, Out] with Chainable {
  override def kala: KalaVibhaga = StriderObj.segmentKala

  def apply(stepName: String, agentId: Value[String], windowSeconds: Int = Segments.DefaultWindowSeconds, overlapSeconds: Int = Segments.DefaultOverlapSeconds): Post[Nothing] = {
    val segments = Segments.forSession("segmentsForSession", Env.sessionId, Value(windowSeconds), Value(overlapSeconds))
    val segment = segments.resultValue(len(segments.resultValue) - 1)
    
    val args = RunWorkflowArgs(
      str(s"${name}-SegKala$${WORKFLOW_ENV}"),
      obj(StriderInput[Params](agentId, segment.flatMap(_.end), Value(windowSeconds), Value(overlapSeconds))),
      connector_params = ConnectorParams(true)
    )
    val call: Post[Nothing] = Call(
      stepName,
      CallOperations.WorkflowRun,
      obj(args)
    )

    Try(s"${stepName}_try", List[Step[_, _]](segments, call) -> call.resultValue)
  }
}

trait SessionStrider[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: us.awfl.utils.Sadhaka[Params])
    extends Strider[In, Params, Out] {
  override def kala: KalaVibhaga = StriderObj.segmentKala.session
}

trait WeekStrider[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: us.awfl.utils.Sadhaka[Params])
    extends Strider[In, Params, Out] {
  override def kala: KalaVibhaga = StriderObj.segmentKala.week
}

trait TermStrider[In, Params, Out](using spec: Spec[Out], yoj: Yoj[In], ista: Ista[Out], sadhaka: us.awfl.utils.Sadhaka[Params])
    extends Strider[In, Params, Out] {
  override def kala: KalaVibhaga = StriderObj.segmentKala.term
}
