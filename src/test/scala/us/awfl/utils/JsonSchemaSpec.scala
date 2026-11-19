package us.awfl.utils

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.services.Llm

// Simple case class under test
case class ConvoSummary(title: Value[String], summary: Value[String])

object ConvoSummary:
  // Provide an Ista instance (not strictly required for shape checks, but mirrors real usage)
  given Ista[ConvoSummary] = Ista("summaries") { kala =>
    """Summarize the convo, return a JSON in this format:
    { "title": "(short title for the convo, ommit if no information is provided)", "summary": "(Summary here)" }

    """.stripMargin +
    (kala match {
      case _: SegKala     => "Don't repeat information already contained in previous summaries and extracted info."
      case _: SessionKala => "Refine (or initiate) the whole session summary."
      case _: WeekKala    => "Refine (or initiate) the whole week summary."
      case _: TermKala    => "Refine (or initiate) the whole term summary."
    })
  }

class JsonSchemaSpec extends AnyFunSuite with Matchers {

  // Provide a test KalaVibhaga (required by JsonSchema derivation APIs)
  given KalaVibhaga = SegKala(
    sessionId = str(CelStr("test-session")),
    end = Value[Double](0),
    windowSeconds = Value[Double](1200)
  )

  // Generic helper to exercise type-parameter usage of toolDefPropertyWithKala
  // Must be inline so that T is known at the call-site and derivation can specialize
  private inline def propOf[T: Spec](using KalaVibhaga): Llm.ToolDefProperty = JsonSchema.toolDefPropertyWithKala[T]

  test("toolDefPropertyWithKala[ConvoSummary] yields object with string properties and both required") {
    val prop: Llm.ToolDefProperty = JsonSchema.toolDefPropertyWithKala[ConvoSummary]

    prop.`type` shouldBe "object"
    prop.properties.keySet shouldBe Set("title", "summary")
    prop.properties("title").`type` shouldBe "string"
    prop.properties("summary").`type` shouldBe "string"

    // required should be present (non-nil)
    prop.required should not be (OptList.nil[String])
  }

  test("documentForWithKala[ConvoSummary] has string properties and required = [title, summary]") {
    val doc = JsonSchema.documentForWithKala[ConvoSummary]

    doc("type") shouldBe "object"

    val props = doc("properties").asInstanceOf[Map[String, Any]]
    props.keySet shouldBe Set("title", "summary")

    val titleProp = props("title").asInstanceOf[Map[String, Any]]
    val summaryProp = props("summary").asInstanceOf[Map[String, Any]]
    titleProp("type") shouldBe "string"
    summaryProp("type") shouldBe "string"

    val req = doc("required").asInstanceOf[List[String]]
    req.toSet shouldBe Set("title", "summary")
  }

  test("generic type-parameter call: propOf[T] compiles and returns expected shape") {
    val p = propOf[ConvoSummary]
    p.`type` shouldBe "object"
    p.properties.keySet shouldBe Set("title", "summary")
    p.properties("title").`type` shouldBe "string"
    p.properties("summary").`type` shouldBe "string"
  }

  test("Ista.tool builds Llm.Tool with parameters from JsonSchema.toolDefPropertyWithKala via summon[Ista[ConvoSummary]].tool(kala)") {
    val maybeTool = summon[Ista[ConvoSummary]].tool(summon[KalaVibhaga])
    maybeTool should not be None

    val toolDefProp: Llm.ToolDefProperty = maybeTool.get match
      case Obj(Llm.Tool(_, Obj(Llm.ToolFunctionDef(_, _, Obj(p))))) => p
      case other => fail(s"Unexpected tool value: ${other}")

    toolDefProp.`type` shouldBe "object"
    toolDefProp.properties.keySet shouldBe Set("title", "summary")
    toolDefProp.properties("title").`type` shouldBe "string"
    toolDefProp.properties("summary").`type` shouldBe "string"
  }
}
