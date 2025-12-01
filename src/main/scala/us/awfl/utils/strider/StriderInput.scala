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
  segmentEnd: BaseValue[Double] = Value.nil,
  windowSeconds: BaseValue[Int] = Value(Segments.DefaultWindowSeconds),
  overlapSeconds: BaseValue[Int] = Value(Segments.DefaultOverlapSeconds),
  env: BaseValue[Env] = ENV
)
