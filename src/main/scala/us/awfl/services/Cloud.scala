package us.awfl.services

import us.awfl.dsl._
import us.awfl.utils.Env
import us.awfl.utils.{post, Post}

object Cloud {
  case class StartParams(sessionId: Value[String], image: Value[String])

  def start(env: Env, image: Value[String]): Post[Boolean] = {
    post[StartParams, Boolean]("startProducer", "producer/start", obj(StartParams(env.sessionId, image)), env = env)
  }

  def stop(env: Env): Post[Boolean] = {
    post[Nothing, Boolean]("stopProducer", "producer/stop", Value.nil, env = env)
  }
}
