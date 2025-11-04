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

case class Quiz(name: Field, subject: Field, contents: Field, score: Field, comments: Field)

given Ista[Quiz] = Ista("quizes", buildList("buildQuizIsta", List(ChatMessage("system", str(
  """Return quizes given to the student. Produce the quiz, questions and answers accurately. Quizes are one of the most important elements of a class since they give a hard metric as to the student's retention and progress in a subject.
  | - name: Name the quiz to give an idea of the material covered.
  | - subject: The subject of the class this quiz was given in.
  | - contents: Full details of questions and the student's answers.
  | - score: The student's score.
  | - comments: Comments on the student's performance and progress in the lesson and subject overall.
  """.stripMargin
)))))

case class Essay(prompt: Field, name: Field, subject: Field, contents: Field, grade: Field, comments: Field)

given Ista[Essay] = Ista("essays", buildList("buildEssayIsta", List(ChatMessage("system", str(
  """Return any essays written by the student. Include these fields:
  | - prompt: The topic, question or prompt that the student is writing off of.
  | - name: Name of the essay.
  | - subject: The subject of the class for which the essay is written.
  | - contents: The contents of the essay.
  | - grade: Your grade for the essay, should be appropriate for grade level and the student's own progression.
  | - comments: Thoughts, corrections, feedback for the essay. Relate to the student's writing focus areas and overall progression in the semester.
  """.stripMargin
)))))

case class Project(name: Field, subject: Field, contents: Field, grade: Field, comments: Field)

given Ista[Project] = Ista("projects", buildList("buildProjectIsta", List(ChatMessage("system", str(
  """Return any projects completed by the student. Include the following fields:
  | - name: Name of the project.
  | - subject: The subject the project is related to.
  | - contents: Detailed description of the project and how it was executed.
  | - grade: Evaluation or score given.
  | - comments: Feedback on the project including creativity, execution, and learning outcomes.
  """.stripMargin
)))))

case class Activity(name: Field, subject: Field, contents: Field, grade: Field, comments: Field)

given Ista[Activity] = Ista("activities", buildList("buildActivityIsta", List(ChatMessage("system", str(
  """Return any class activities the student participated in. Include the following fields:
  | - name: Name of the activity.
  | - subject: Subject area related to the activity.
  | - contents: Description of the activity.
  | - grade: Any score or evaluation if applicable.
  | - comments: Feedback or insights from the activity.
  """.stripMargin
)))))

case class Lesson(subject: Field, coveredMaterial: Field, quizes: ListValue[Quiz], essays: ListValue[Essay], projects: ListValue[Project], activities: ListValue[Activity])

object Lesson {
  given Ista[Lesson] = Ista("lessons", {
    val quizIsta = summon[Ista[Quiz]].build
    val essayIsta = summon[Ista[Essay]].build
    val projectIsta = summon[Ista[Project]].build
    val activityIsta = summon[Ista[Activity]].build

    val lessonIsta = buildList("buildLessonIsta", List(ChatMessage("system", str(
      """Please pick out the incremental milestones of this on ongoing lession.
      |The information you provide serves as the 'teacher's notes' which form a record of the student's progress, the work they've completed and the academic acomplishments they've achieved.
      |After reviewing the conversation segment, return these pieces of information:
      | - subject: The subject being covered.
      | - coveredMaterial: Detail the material covered, progress relative to the semester lesson plan, level reletaive to the studen't current grade, student's retention and comprehension, areas of strength and areas requiring follow-up.
      | - quizes: Return any quizes given along with questions and answers.
      | - essays: Return any essays written by the student.
      | - projects: Include any projects the student completed/worked on.
      | - activites: Detail any activites the student participated in.
      """.stripMargin
    ))))

    val joinStep = join(
      "joinLessonIsta",
      quizIsta.resultValue,
      essayIsta.resultValue,
      projectIsta.resultValue,
      activityIsta.resultValue,
      lessonIsta.resultValue
    )

    Block("lessonIstaBlock", List[Step[_, _]](quizIsta, essayIsta, projectIsta, activityIsta, lessonIsta, joinStep) -> joinStep.resultValue)
  })

  given lessonYoj(using kala: KalaVibhaga, stepName: StepName): Yoj[Lesson] = Yoj("lessons", "Lesson info from an earlier segment of the conversation:\r")
}