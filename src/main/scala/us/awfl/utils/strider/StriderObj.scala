package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.utils.Yoj
import us.awfl.utils.Convo
import us.awfl.utils.Segments
import us.awfl.utils.Locks
import us.awfl.services.Firebase
import us.awfl.services.Firebase.Segment
import us.awfl.services.Firebase.create
import us.awfl.services.Firebase.update
import us.awfl.utils.SegKala

object StriderObj {
  val inputVal = init[StriderInput]("input")
  val input = inputVal.get
  given Convo.SessionId = Convo.SessionId(input.sessionId)

  private val windowSeconds  = Value[Double](CelFunc("default", input.windowSeconds, Segments.DefaultWindowSeconds))
  private val overlapSeconds = Value[Int](CelFunc("default", input.overlapSeconds, Segments.DefaultOverlapSeconds))

  val segments: Step[SegKala, ListValue[SegKala]] =
    Segments.forSession("segmentsForSession", input.sessionId, windowSeconds, overlapSeconds)

  val segment       = segments.resultValue(len(segments.resultValue) - 1)
  val segmentKala   = SegKala(input.sessionId, input.segmentEnd, windowSeconds)
}
