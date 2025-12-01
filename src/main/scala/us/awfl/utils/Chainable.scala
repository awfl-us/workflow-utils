package us.awfl.utils

import us.awfl.dsl._
import us.awfl.core

case class ChainableInput[T, P](input: BaseValue[T], params: BaseValue[P], env: BaseValue[Env] = ENV)

case class Chain[Out](input: BaseValue[_], init: List[(BaseValue[_], core.Workflow)], lastParams: BaseValue[_], lastWorkflow: core.Workflow)(using eq: lastWorkflow.Result =:= Out) {
  def andThen[T]
    (next: Linkable)
    (params: BaseValue[next.Params])
    (
      using in: next.Input =:= ChainableInput[Out, next.Params],
      out: next.Result =:= T
    ): Chain[T] = {
      Chain(input, init :+ (lastParams -> lastWorkflow), params, next)
    }
}

trait Chainable extends core.Workflow {
  def runSync(input: BaseValue[this.Input]): Chain[this.Result] = {
    Chain(input, List(), Value.nil[String], this)
  }
}

trait Linkable extends core.Workflow {
  type Params
}
