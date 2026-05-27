package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import io.circe.generic.auto._
import io.circe.Encoder

// Use plural jobs prefix for all HTTP calls and OIDC audience
case class Auth(`type`: Value[String] = str("OIDC"), audience: Value[String] = Env.BASE_URL)

type Post[Out] = Step[PostResult[Out], Value[PostResult[Out]]]

type Get[Out] = Step[PostResult[Out], Value[PostResult[Out]]]

type Patch[Out] = Step[PostResult[Out], Value[PostResult[Out]]]

type Delete[Out] = Step[PostResult[Out], Value[PostResult[Out]]]

def headers(env: Env) = Map("x-user-id" -> env.userId, "x-project-id" -> env.projectId)

case class PostRequest[T](
  url: Value[String],
  body: BaseValue[T],
  auth: Auth = Auth(),
  headers: Map[String, Value[String]] = headers(Env.get)
)
case class GetRequest(url: Value[String], auth: Auth = Auth(), headers: Map[String, Value[String]] = headers(Env.get))
case class PostResult[T](body: Value[T])
implicit def postResult[T: Spec]: Spec[PostResult[T]] = Spec { resolver =>
  PostResult(resolver.in[T]("body"))
}

// POST helpers
def postV[In, Out: Spec](name: String, urlPath: Value[String], body: BaseValue[In], auth: Auth = Auth(), env: Env = Env.get): Post[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val postArgs = PostRequest[In](
    url = absUrl,
    body = body,
    auth = auth,
    headers = headers(env)
  )
  Call[PostRequest[In], PostResult[Out]](name, "http.post", obj(postArgs))
}

def post[In, Out: Spec](name: String, relativePath: String, body: BaseValue[In], auth: Auth = Auth(), env: Env = Env.get): Post[Out] =
  postV(name, str(relativePath), body, auth, env)

// GET helpers
def getV[Out: Spec](name: String, urlPath: Value[String], auth: Auth = Auth(), env: Env = Env.get): Get[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val getArgs = GetRequest(
    url = absUrl,
    auth = auth,
    headers = headers(env)
  )
  Call[GetRequest, PostResult[Out]](name, "http.get", obj(getArgs))
}

def get[Out: Spec](name: String, relativePath: Value[String], auth: Auth = Auth(), env: Env = Env.get): Get[Out] =
  getV(name, relativePath, auth, env)

def get[Out: Spec](name: String, relativePath: String, auth: Auth): Get[Out] =
  getV(name, str(relativePath), auth)

// PATCH helpers
def patchV[In, Out: Spec](name: String, urlPath: Value[String], body: BaseValue[In], auth: Auth = Auth(), env: Env = Env.get): Patch[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val patchArgs = PostRequest[In](
    url = absUrl,
    body = body,
    auth = auth,
    headers = headers(env)
  )
  Call[PostRequest[In], PostResult[Out]](name, "http.patch", obj(patchArgs))
}

def patch[In, Out: Spec](name: String, relativePath: String, body: BaseValue[In], auth: Auth = Auth(), env: Env = Env.get): Patch[Out] =
  patchV(name, str(relativePath), body, auth, env)

def delete[Out: Spec](name: String, urlPath: Value[String], auth: Auth = Auth(), env: Env = Env.get): Delete[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val getArgs = GetRequest(
    url = absUrl,
    auth = auth,
    headers = headers(env)
  )
  Call[GetRequest, PostResult[Out]](name, "http.delete", obj(getArgs))
}
