package us.awfl.utils

import us.awfl.dsl._

case class Timestamped[T](
  value: BaseValue[T],
  create_time: BaseValue[Double],
  cost: BaseValue[Double],
  execId: Value[String] = Exec.currentExecId,
)
