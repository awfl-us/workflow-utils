package us.awfl.utils

import us.awfl.dsl
import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.Cel._
import us.awfl.dsl.CelOps._
import us.awfl.ista.ChatMessage
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.SegKala
import us.awfl.utils.SessionKala
import us.awfl.utils.WeekKala
import us.awfl.utils.TermKala
import us.awfl.services.Context as Ctx
import us.awfl.utils.post
import us.awfl.utils.Post
import io.circe.Json
import us.awfl.utils.Env

// Server-composed TopicContext Yoj wrapper. Delegates to JS endpoint and returns joined messages.
object TopicContextYoj {
  // ----- Rendered Yoj (messages) -----
  case class YojResult(yoj: ListValue[ChatMessage])

  // Optional model payload that can be sent to /run to control composition
  final case class Intro(system: Option[String] = None)
  // Added children to support nested composition
  final case class ComponentSpec(
    kind: String,
    name: Option[String] = None,
    framing: Option[String] = None,
    value: Option[String] = None,
    children: List[ComponentSpec] = List.empty
  )
  object ComponentSpec {
    // Break implicit-derivation recursion for children: provide a concrete SpecBuilder for ListValue[ComponentSpec]
    given componentListSpecBuilder: dsl.auto.SpecBuilder[dsl.ListValue[ComponentSpec]] with
      def build(fieldName: String): dsl.Resolver => dsl.ListValue[ComponentSpec] = _.list(fieldName)
  }

  final case class FilterSpec(name: String, options: Option[Map[String, Json]] = None)
  final case class Model(
    intro: Option[Intro],
    promoteUpstream: Boolean,
    components: ListValue[ComponentSpec],
    filters: ListValue[FilterSpec]
  )

  private def encodeKala(k: KalaVibhaga): BaseValue[Ctx.Kala] = k match {
    case seg: SegKala      => Ctx.segKala(seg.sessionId, seg.end, seg.windowSeconds)
    case sesh: SessionKala => Ctx.sessionKala(sesh.sessionId, sesh.end)
    case week: WeekKala    => Ctx.weekKala(week.end)
    case term: TermKala    => Ctx.termKala(term.termBegin, term.end)
  }

  case class TopicArgs(
    kala: BaseValue[Ctx.Kala],
    presetId: BaseValue[String] = Value("null"),
    includeDocId: BaseValue[Boolean] = Value(true),
    model: BaseValue[Model] = Value("null")
  )

  // Return the raw POST step so callers can access .resultValue.flatMap(_.body).flatMap(_.yoj)
  def run(using kala: KalaVibhaga): Post[YojResult] =
    post[TopicArgs, YojResult](
      "TopicContextYoj_run",
      "context/topicContextYoj/run",
      obj(TopicArgs(encodeKala(kala), str("TopicContext")))
    )

  def run(includeDocId: Boolean)(using kala: KalaVibhaga): Post[YojResult] =
    post[TopicArgs, YojResult](
      "TopicContextYoj_run_with_docId",
      "context/topicContextYoj/run",
      obj(TopicArgs(encodeKala(kala), str("TopicContext"), includeDocId = Value(includeDocId)))
    )

  def runWithModel(model: BaseValue[Model], includeDocId: Option[Boolean] = None)(using kala: KalaVibhaga): Post[YojResult] = {
    val args = includeDocId match {
      case Some(flag) => TopicArgs(encodeKala(kala), includeDocId = Value(flag), model = model)
      case None       => TopicArgs(encodeKala(kala), model = model)
    }
    post[TopicArgs, YojResult](
      "TopicContextYoj_run_with_model",
      "context/topicContextYoj/run",
      obj(args)
    )
  }
}
