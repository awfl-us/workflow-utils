package us.awfl.utils

import io.circe.parser.parse
import us.awfl.dsl.*
import us.awfl.dsl.auto.given

object SchemaExampleSmoke {
  case class Detail(active: Value[Boolean], count: Value[Int])
  case class Summary(title: Value[String], items: ListValue[Detail], cost: Value[Double])

  def main(args: Array[String]): Unit = {
    val sample = parse(SchemaExample[Summary]).fold(throw _, identity)
    assert(sample.hcursor.get[String]("title").toOption.contains("example"))
    assert(sample.hcursor.downField("items").downArray.get[Boolean]("active").toOption.contains(false))
    assert(sample.hcursor.downField("items").downArray.get[Int]("count").toOption.contains(0))
    assert(sample.hcursor.get[Double]("cost").toOption.contains(0.0))
    assert(!SchemaExample[Summary].contains("schemaExample"))
    println("SCHEMA_EXAMPLE_SMOKE_PASS")
  }
}
