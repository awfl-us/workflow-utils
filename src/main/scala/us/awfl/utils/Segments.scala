package us.awfl.utils

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.services.Firebase
import us.awfl.services.Firebase.Segment

object Segments {
  // Canonical segmentation configuration
  val DefaultWindowSeconds: Int  = 60 * 20
  val DefaultOverlapSeconds: Int = 60 * 4

  /**
   * Build segment windows for a session using the Firebase helper, then convert
   * them to SegKala instances used across the codebase.
   */
  def forSession(
      stepPrefix:    String,
      sessionId:     Value[String],
      windowSeconds: Value[Double] = Value(DefaultWindowSeconds),
      overlapSecs:   Value[Int] = Value(DefaultOverlapSeconds)
  ): Step[SegKala, ListValue[SegKala]] = {
    // Source collection of raw chat messages for this session
    val messagesCollection: BaseValue[String] =
      summon[Ista[ChatMessage]].convoCollection(sessionId)

    // Fetch segment boundaries
    val raw = Firebase.segmentsForSession(
      s"${stepPrefix}_fetch",
      messagesCollection,
      windowSeconds,
      overlapSecs
    )

    // Convert Firebase.Segment -> SegKala with a fixed windowSeconds
    val convert = For[Segment, SegKala](
      s"${stepPrefix}_convert",
      raw.resultValue.flatMap(_.segments)
    ) { seg =>
      val endTime = seg.flatMap(_.end)
      List() -> obj(SegKala(sessionId, endTime, windowSeconds))
    }

    // Expose a single step that yields List[SegKala]
    Block(
      s"${stepPrefix}_block",
      List[Step[_, _]](raw, convert) -> convert.resultValue
    )
  }
}
