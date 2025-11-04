package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import io.circe.Encoder

def zip[A, B, Out: Spec](
  name: String,
  left: ListValue[A],
  right: ListValue[B]
)(merge: (Value[A], Value[B]) => (List[Step[_, _]], BaseValue[Out])): ForRange[Out] = {
  ForRange[Out](name, 0, len(left)) { i =>
    val a = left(i)
    val b = right(i)
    merge(a, b)
  }
}

