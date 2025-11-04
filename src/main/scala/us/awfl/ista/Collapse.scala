package us.awfl.ista

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.utils.Ista
import us.awfl.utils.Yoj
import us.awfl.utils.KalaVibhaga
import us.awfl.utils.Convo.StepName

// Schema for collapsed context groups
case class CollapsedItem(`type`: Field, id: Field)
case class CollapsedGroup(name: Field, description: Field, items: ListValue[CollapsedItem])
case class CollapseResponse(groups: ListValue[CollapsedGroup])

object CollapseResponse {
  // Ista prompt for generating collapsed groups from context
  given Ista[CollapseResponse] = Ista(
    "collapsed",
    buildList(
      "buildCollapseIsta",
      List(
        ChatMessage(
          "system",
          str(
            """
            You are collapsing older conversation messages into compact, named groups that can be expanded later.
            Return valid JSON with this exact shape:
            {
              "groups": [
                {
                  "name": "UPPER_SNAKE_CASE_GROUP_NAME",
                  "description": "Concise description of what this group contains. Surface any concise details that might still be relevant to current and future tasks.",
                  "items": [
                    { "type": "message",   "id": "<message_docId (UUID, required)>" },
                    { "type": "collapsed", "id": "<UPPER_SNAKE_CASE_GROUP_NAME, required>" }
                  ]
                }
              ]
            }

            Notes:
            - Use UPPER_SNAKE_CASE for group names.
            - Prefer referencing concrete message docIds when available; you may also nest other groups via type='collapsed'.
            - Do not repeat information already captured in previous summaries; focus on no-longer-active parts of the convo.
            - Do not include any extra fields beyond those specified.
            - Do not collapse messages relevant to the current task/discussion or possibly relevant to near-future development. These should not be collapsed!
            - Do not return any groups with empty message list, that defeats the purpose of collapsing the messages.
            - Be fiarly aggressive because reduntant or unneeded information cloggs context. Aim for getting the total context to less than 15k tokens, an exception would be complex, crucial moments of an important task. Otherwise, stay focused on quality of production, but tay midful to the overall vision.
            """.stripMargin
          )
        )
      )
    )
  )
}
