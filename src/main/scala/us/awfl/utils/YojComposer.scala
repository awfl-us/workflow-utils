package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
import us.awfl.utils.TopicContextYoj
import us.awfl.utils.Yoj

// Generic composer for assembling and running TopicContextYoj component trees.
object YojComposer {
  def composed(
    name: String,
    childComponents: List[TopicContextYoj.ComponentSpec],
    intro: Option[String] = None,
    promoteUpstream: Boolean = true,
    filters: ListValue[TopicContextYoj.FilterSpec] = ListValue.empty[TopicContextYoj.FilterSpec]
  )(using kala: KalaVibhaga): Step[ChatMessage, ListValue[ChatMessage]] = {
    
    val spec: TopicContextYoj.ComponentSpec =
      TopicContextYoj.ComponentSpec(
        kind = "yoj",
        name = Some(name),
        children = childComponents
      )

    val components = buildList[TopicContextYoj.ComponentSpec](
      s"${name}_components_${Yoj.kalaName}",
      List(spec)
    )

    val model = Try(
      s"${name}_model_${Yoj.kalaName}",
      List(components) -> obj(
        TopicContextYoj.Model(
          intro = intro.map(s => TopicContextYoj.Intro(system = Some(s))),
          promoteUpstream = promoteUpstream,
          components = components.resultValue,
          filters = filters
        )
      )
    )

    val call = TopicContextYoj.runWithModel(model.resultValue)
    val items = For[ChatMessage, ChatMessage](
      s"for_${name}_items_${Yoj.kalaName}",
      call.resultValue.flatMap(_.body).flatMap(_.yoj),
    ) { item => List() -> item }

    Block(
      s"${name}YojBlock_${Yoj.kalaName}",
      List[Step[_, _]](/*children, */components, model, call, items) -> items.resultValue
    )
  }

  // Build a parent ComponentSpec with children using buildList at runtime.
  // Returns a Step because buildList must execute to produce the ListValue.
  def parentComponent(
    name: String,
    children: List[TopicContextYoj.ComponentSpec],
    framing: Option[String] = None
  ): TopicContextYoj.ComponentSpec = {
    // val buildChildren = buildList[TopicContextYoj.ComponentSpec](
    //   s"${name}_parent_children",
    //   children
    // )
    // Block(
    //   s"${name}_parentComponent",
    //   List(buildChildren) -> obj(
    //     TopicContextYoj.ComponentSpec(
    //       kind = "yoj",
    //       name = Some(name),
    //       framing = framing,
    //       children = buildChildren.resultValue
    //     )
    //   )
    // )
    TopicContextYoj.ComponentSpec(
      kind = "yoj",
      name = Some(name),
      framing = framing,
      children = children
    )
  }
}
