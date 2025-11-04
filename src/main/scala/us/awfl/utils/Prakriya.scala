package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.utils.Convo.SessionId
import us.awfl.utils.Timestamped
import us.awfl.services.Firebase.list
import us.awfl.services.Firebase.ListResult
import us.awfl.services.Firebase.create
import us.awfl.services.Firebase.ListItem
import us.awfl.services.Firebase.update
import us.awfl.services.Firebase.listAt
import us.awfl.services.Firebase
import us.awfl.utils.Convo.StepName
import us.awfl.utils.TopicContextYoj

// यो जना — पदानां युक्तिः वा संयोजनम्। 
// इयं रचना, यया इष्टस्य सिद्धिः साध्यते। 
// पदरचनायाḥ स्वरूपनियमनं योजना भवति।
case class Yoj[T](
  name: String,
  // Old build signature: produce ChatMessages for this Yoj at the given kala
  buildYoj: KalaVibhaga => Step[ChatMessage, ListValue[ChatMessage]],
  // ComponentSpec value (no kala function needed)
  component: TopicContextYoj.ComponentSpec
) {
  def build(using kala: KalaVibhaga): Step[ChatMessage, ListValue[ChatMessage]] = buildYoj(kala)
}
// Yojanā — "Arrangement or alignment of semantic units."
// The compositional method used to align Pada or Ista units to realize meaningful structure.

object Yoj:
  def kalaName(using kala: KalaVibhaga) = kala match {
    case _: SegKala     => "SegKala"
    case _: SessionKala => "SessionKala"
    case _: WeekKala    => "WeekKala"
    case _: TermKala    => "TermKala"
  }

  // Component factory for TopicContextYoj
  // Canonical mapping (from ContextAgent):
  // - kind values: "yoj" (default), "ista" (supported, not default), "literal" (fixed text)
  // - yoj|ista require name; optional framing; no value
  // - literal requires value; no name/framing
  // - Unqualified names default to kind="yoj"
  def component(name: String): TopicContextYoj.ComponentSpec =
    TopicContextYoj.ComponentSpec(kind = "yoj", name = Some(name.trim))

  def component(name: String, framing: String): TopicContextYoj.ComponentSpec =
    component(name).copy(framing = Some(framing))

  // Optional prefixed form for convenience: "yoj:NAME", "ista:NAME", "literal:VALUE"
  def componentFrom(spec: String, framing: Option[String] = None): TopicContextYoj.ComponentSpec = {
    val s = spec.trim
    val (kind, rest) =
      if s.startsWith("yoj:") then ("yoj", s.drop(4))
      else if s.startsWith("yoj/") then ("yoj", s.drop(4))
      else if s.startsWith("ista:") then ("ista", s.drop(5))
      else if s.startsWith("ista/") then ("ista", s.drop(5))
      else if s.startsWith("literal:") then ("literal", s.drop(8))
      else ("yoj", s)

    kind match
      case "literal" => TopicContextYoj.ComponentSpec(kind = "literal", value = Some(rest))
      case "ista"    => TopicContextYoj.ComponentSpec(kind = "ista", name = Some(rest), framing = framing)
      case _          => TopicContextYoj.ComponentSpec(kind = "yoj", name = Some(rest), framing = framing)
  }

  // Build a Yoj that contributes a single component (and a default messages builder)
  // The messages builder renders this single component via TopicContextYoj
  def apply[T: Spec](name: String, framing: String): Yoj[T] =
    new Yoj[T](
      name,
      // buildYoj: render this component through TopicContextYoj
      { kala =>
        given KalaVibhaga = kala

        val comp = component(name, framing)
        val components = buildList[TopicContextYoj.ComponentSpec](
          s"${name}_components_${Yoj.kalaName}",
          List(comp)
        )

        val model = Try(
          s"${name}_model_${Yoj.kalaName}",
          List(components) -> obj(
            TopicContextYoj.Model(
              intro = None,
              promoteUpstream = false,
              components = components.resultValue,
              filters = ListValue.empty[TopicContextYoj.FilterSpec]
            )
          )
        )

        val call = TopicContextYoj.runWithModel(model.resultValue)
        val items = For[ChatMessage, ChatMessage](
          s"for_${name}_items_${Yoj.kalaName}",
          call.resultValue.flatMap(_.body).flatMap(_.yoj)
        ) { item => List() -> item }

        Block(s"${name}_yoj_block_${Yoj.kalaName}", List[Step[_, _]](components, model, call, items) -> items.resultValue)
      },
      component(name, framing)
    )

  // Convenience: supply a framing + custom build function
  // def withFraming[T](
  //   name: String,
  //   framing: String
  // )(build: KalaVibhaga => Step[ChatMessage, ListValue[ChatMessage]]): Yoj[T] =
  //   new Yoj[T](name, build, component(name, framing))

  // Convenience: supply only a custom build function; component defaults to kind="yoj" with the given name
  // def apply[T](
  //   name: String,
  //   build: KalaVibhaga => Step[ChatMessage, ListValue[ChatMessage]]
  // ): Yoj[T] =
  //   new Yoj[T](name, build, component(name))

// इष्टः — यः अभिलषितः अर्थः। 
// प्रयोजनदृष्ट्या अपेक्षितः परिणामः। 
// उदाहरणतः, यं विषयं प्राप्तुं वाञ्छति प्रणेता, स इष्टः।
case class Ista[T: Spec](name: String, build: Step[ChatMessage, ListValue[ChatMessage]]) {
  def convoCollection(sessionId: Value[String]) = str(("convo.sessions/": Cel) + sessionId.cel + "/" + name)

  def write(stepName: String, contents: BaseValue[T], at: BaseValue[Double], cost: BaseValue[Double])(using kala: KalaVibhaga): Post[Nothing] = {
    val ts = obj(Timestamped(contents, at, cost))
    val writeStep: Post[Nothing] = kala match {
      case SegKala(sessionId, end, windowSeconds) =>
        // Create the child document, then update the parent session doc's updated_at
        val newId = Try(s"${stepName}_newId", List() -> Value("uuid.generate()"))
        val createChild = create(s"${stepName}_write_${name}Ista_SegKala", convoCollection(sessionId), newId.resultValue, ts)
        val updateParent = update(
          s"${stepName}_update_session_update_time",
          str("convo.sessions": Cel),
          sessionId,
          obj(Map("update_time" -> at))
        )
        Block(
          s"${stepName}_seg_write_with_session_update",
          List[Step[_, _]](newId, createChild, updateParent) -> createChild.resultValue
        )
      case SessionKala(sessionId, sessionEnd) => update(s"${stepName}_write_${name}Ista", str(("log.sessions.": Cel) + name), sessionId, ts)
      case WeekKala(weekEnd)                  => update(s"${stepName}_write_${name}Ista_WeekKala", str(("log.weekly.": Cel) + name), Value(CelFunc("time.format", weekEnd.cel, "America/New_York")), ts)
      case TermKala(termBegin, termEnd)       => update(s"${stepName}_write_${name}Ista_TermKala", str(("log.terms.": Cel) + name), Value(CelFunc("time.format", termEnd, "America/New_York")), ts)
    }
    contents match {
      case Obj(value)      => writeStep
      case _: Resolved[?] =>
        Switch(s"${stepName}_switch", List(
          (contents.cel !== Value("null")) -> writeStep.fn,
          (true: Cel) -> (List() -> Value("null"))
        ))
    }
  }

  def read[T: Spec](using kala: KalaVibhaga): Step[Timestamped[T], ListValue[Timestamped[T]]] = {
    val log = kala.log(s"read_${name}Ista_logKala")
    val results: ValueStep[ListResult[Timestamped[T]]] = kala match {
      case SegKala(sessionId, end, windowSeconds) => list(s"read_${name}Ista_convoSegKala", convoCollection(sessionId), Value(end.cel - windowSeconds.cel), end)
      case SessionKala(sessionId, sessionEnd)      => list(s"read_${name}Ista_SessionKala", str(("log.sessions.": Cel) + name), Value(0), sessionEnd)
      case WeekKala(weekEnd)                       => list(s"read_${name}Ista_WeekKala", str(("log.weekly.": Cel) + name), Value(weekEnd.cel - 60 * 60 * 24 * 7), weekEnd)
      case TermKala(begin, end)                    => list(s"read_${name}Ista_QuaterKala", str(("log.terms.": Cel) + name), begin, end)
    }
    val forItems = For[ListItem[Timestamped[T]], Timestamped[T]](s"for_${name}Ista", results.resultValue.flatMap(_.documents)) { item =>
      List(Log(s"log_${name}Ista_read", str(("Read items: ": Cel) + CelFunc("json.encode_to_string", item.cel)))) -> item.flatMap(_.data)
    }
    Block(s"${name}_readIstaBlock", List[Step[_, _]](log, results, forItems) -> forItems.resultValue)
  }
}
// Iṣṭaḥ — "That which is desired or intended."
// A target or goal that the system wishes to extract or realize from context.

// कालविभागः — समयस्य विभागः, त्रैकालिकविभाजनम्। 
// संवादी प्रक्रमायाः कालपरिमाणनुसारं विभागः। 
// उदाहरणतः, खण्डः (Segment), सत्यम् (Session), त्रिमासिकम् (Quarter) इत्यादयः।
sealed trait KalaVibhaga {
  def collection(name: String): BaseValue[String]
  def *(times: Int): KalaVibhaga
  def log(name: String): Log
  val end: BaseValue[Double]

  def segment(using sessionId: SessionId): SegKala = SegKala(sessionId.value, end, Value(obj(60 * 20)))
  def week: WeekKala = {
    val original = 259200: Cel
    val weekSeconds: Cel = 7 * 24 * 60 * 60
    val n = (end.cel - original) `//` weekSeconds
    WeekKala(Value(original + ((n + 1) * weekSeconds)))
  }
  def term: TermKala = {
    val original = 0: Cel
    val quarterSeconds: Cel = 365.230769 * 24 * 60 * 60 / 4
    val n = ((end.cel - original) `//` quarterSeconds)
    TermKala(Value(original + (n * quarterSeconds)), Value(original + ((n + 1) * quarterSeconds)))
  }
}

case class SegKala(sessionId: Value[String], end: BaseValue[Double], windowSeconds: BaseValue[Double]) extends KalaVibhaga {
  override def collection(name: String): BaseValue[String] = str(("convo.sessions/": Cel) + sessionId.cel + "/" + name)
  override def *(times: Int): KalaVibhaga = SegKala(sessionId, end, Value(windowSeconds.cel * times))
  override def log(name: String): Log = Log(name, str(("SegKala: ": Cel) + CelFunc("time.format", end.cel, "America/New_York")))

  def session: SessionKala = SessionKala(sessionId, end)
}

case class SessionKala(sessionId: Value[String], sessionEnd: BaseValue[Double]) extends KalaVibhaga {
  override def collection(name: String): BaseValue[String] = str(("convo.sessions/": Cel) + sessionId.cel + "/" + name)
  override def *(times: Int): KalaVibhaga = this
  override def log(name: String): Log = Log(name, str(("SessionKala: ": Cel) + CelFunc("time.format", sessionEnd.cel, "America/New_York")))
  override val end: BaseValue[Double] = sessionEnd
}

case class WeekKala(weekEnd: BaseValue[Double]) extends KalaVibhaga {
  override def collection(name: String): BaseValue[String] = str(("log.sessions.": Cel) + name)
  override def *(times: Int): KalaVibhaga = TermKala(Value(weekEnd.cel - 60 * 60 * 24 * 7 * times), weekEnd)
  override def log(name: String): Log = Log(name, str(("WeekKala: ": Cel) + CelFunc("time.format", weekEnd.cel, "America/New_York")))
  override val end: BaseValue[Double] = weekEnd
}

case class TermKala(termBegin: BaseValue[Double], termEnd: BaseValue[Double]) extends KalaVibhaga {
  override def collection(name: String): BaseValue[String] = str(("log.weekly.": Cel) + name)
  override def *(times: Int): KalaVibhaga = TermKala(Value(termEnd.cel - ((termEnd.cel - termBegin.cel) * times)), termEnd)
  override def log(name: String): Log = Log(name, str(("TermKala: ": Cel) + CelFunc("time.format", termEnd.cel, "America/New_York")))
  override val end: BaseValue[Double] = termEnd
}
