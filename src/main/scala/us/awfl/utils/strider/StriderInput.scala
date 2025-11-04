package us.awfl.utils.strider

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.dsl.auto.given
import us.awfl.utils.Segments
import us.awfl.utils.{Env, ENV}

/**
 * Strider workflow input parameters and handy accessors.
 */
case class StriderInput(
  arg1: Value[String],
  arg2: BaseValue[Double],
  arg3: BaseValue[Int] = Value(Segments.DefaultWindowSeconds),
  arg4: BaseValue[Int] = Value(Segments.DefaultOverlapSeconds),
  env: BaseValue[Env] = ENV
) {
  val sessionId: Value[String] = arg1
  val segmentEnd: BaseValue[Double] = arg2
  val windowSeconds: BaseValue[Int] = arg3
  val overlapSeconds: BaseValue[Int] = arg4
}
