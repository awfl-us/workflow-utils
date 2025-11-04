package us.awfl.ista

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.utils.Ista
import us.awfl.utils.Convo
import us.awfl.utils.Yoj
import us.awfl.utils.Convo.SessionId
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Convo.StepName

case class Topic(topic: Field, breadth: Field, depth: Field, startingPoint: Field, endingPoint: Field, details: Field)

given Ista[Topic] = Ista("topics", buildList("buildTopicIsta", List(ChatMessage("system", str(
  """Return topics discussed. Typically a conversation will include 1-3 topics. Provide these details about the topics discussed:
    | - topic: The name of the topic, keeps names of known topics consistent.
    | - breadth: Qualify the breadth of the discussion on this topic in detail.
    | - depth: Qualify the depth of the discussion on this topic.
    | - startingPoint: Where did the discussion on this topic start? (We want to map the the progression, depth and breadth accross multiple conversations over time)
    | - endingPoint: Where did the converstion leave off or conclude?
    | - details: Record the relevant details of the discussion on this topic.

    Don't repeat information already contained in previous summaries and extracted info.
  """.stripMargin
)))))

case class Nugget(details: Field)

given Ista[Nugget] = Ista("nuggets", buildList("buildNuggetIsta", List(ChatMessage("system", str(
  """Extract key nuggets from the conversation. These are the notable questions/conclusions/learnings that will be most interesting post-conversation when revisting the discussed topics.
    |Provide the following field:
    | - details: Details of the info nugget.
  """.stripMargin
)))))

case class Reflection(critique: Field)

given Ista[Reflection] = Ista("reflections", buildList("buildRelfectionIsta", List(ChatMessage("system", str(
  """Optionally, provide reflections on your own performance in the converstation. How could you have served the user better?
    |Any information about the user or context that would have been useful to have earlier?
    |Provide the following field:
    | - critique: Details on what you should have done differently or known beforehand.
  """.stripMargin
)))))

case class Artifact(name: Field, path: Field, `type`: Field, contents: Field, description: Field)

given Ista[Artifact] = Ista("artifacts", buildList("buildArtifactIsta", List(ChatMessage("system", str(
  """Extract all important artifacts (documents, snippets, etc.) from the conversation.
    |These are often the most important information in a convo because they serve as the concrete elements that give a convo it's value.
    |Provide the following fields for each artifact:
    | - name: Name of the artifact (identifier).
    | - path: (If saved file) This should be the path and filename for actual files.
    | - Type of the artifact (informs how to use or interpret)
    | - contents: (Only if not a saved file) Contents of the artifact.
    | - description: Context for the artifact and any important info that needs to accompany it.
  """.stripMargin
)))))

case class TopicInfo(topics: ListValue[Topic], nuggets: ListValue[Nugget], reflections: ListValue[Reflection], artifacts: ListValue[Artifact])

object TopicInfo {
  given Ista[TopicInfo] = Ista("topicInfos", {
    val topicIsta = summon[Ista[Topic]].build
    val nuggetIsta = summon[Ista[Nugget]].build
    val reflectionIsta = summon[Ista[Reflection]].build
    val artifactIsta = summon[Ista[Artifact]].build

    val topicInfoIsta = buildList("buildTopicInfoIsta", List(ChatMessage("system", str(
        """Please extract topic information from the conversation. The idea is to distil important information/conclusions/results from the convo that can then be used to build a system representing the user's preferences, progression and overall goals.
        |Extract the following types of information:
        | - topics: Topics discussed.
        | - nuggets: Usefull nuggets of information to retain.
        | - reflections: Stear your own behavior onto a better heading.
        | - artifacts: Important data chunks to retain.
        """.stripMargin
    ))))

    val joinStep = join(
      "joinTopicInfoIsta",
      topicIsta.resultValue,
      nuggetIsta.resultValue,
      reflectionIsta.resultValue,
      artifactIsta.resultValue,
      topicInfoIsta.resultValue
    )

    Block("topicInfoIstaBlock", List(topicIsta, nuggetIsta, reflectionIsta, artifactIsta, topicInfoIsta, joinStep) -> joinStep.resultValue)
  })

  given Yoj[TopicInfo] = Yoj("topicInfos", "Previously extracted topic info:\r")
}
