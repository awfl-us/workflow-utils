package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import io.circe.generic.auto._
import io.circe.Encoder

// Use plural jobs prefix for all HTTP calls and OIDC audience
case class Auth(`type`: String = "OIDC", audience: BaseValue[String] = Env.BASE_URL)

type Post[Out] = Step[PostResult[Out], Resolved[PostResult[Out]]] with ValueStep[PostResult[Out]]

type Get[Out] = Step[PostResult[Out], Resolved[PostResult[Out]]] with ValueStep[PostResult[Out]]

type Patch[Out] = Step[PostResult[Out], Resolved[PostResult[Out]]] with ValueStep[PostResult[Out]]

val EnvHeaders = Map("x-user-id" -> Env.userId, "x-project-id" -> Env.projectId)

case class PostRequest[T](
  url: BaseValue[String],
  body: BaseValue[T],
  auth: Auth = Auth(),
  headers: Map[String, Field] = EnvHeaders
)
case class GetRequest(url: BaseValue[String], auth: Auth = Auth(), headers: Map[String, Field] = EnvHeaders)
case class PostResult[T](body: Value[T])
implicit def postResult[T: Spec]: Spec[PostResult[T]] = Spec { resolver =>
  PostResult(resolver.in[T]("body"))
}

// POST helpers
def postV[In, Out: Spec](name: String, urlPath: BaseValue[String], body: BaseValue[In], auth: Auth = Auth()): Post[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val postArgs = PostRequest[In](
    url = absUrl,
    body = body,
    auth = auth
  )
  Call[PostRequest[In], PostResult[Out]](name, "http.post", obj(postArgs))
}

def post[In, Out: Spec](name: String, relativePath: String, body: BaseValue[In], auth: Auth = Auth()): Post[Out] =
  postV(name, str(relativePath), body, auth)

// GET helpers
def getV[Out: Spec](name: String, urlPath: BaseValue[String], auth: Auth = Auth()): Get[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val getArgs = GetRequest(
    url = absUrl,
    auth = auth
  )
  Call[GetRequest, PostResult[Out]](name, "http.get", obj(getArgs))
}

def get[Out: Spec](name: String, relativePath: BaseValue[String], auth: Auth = Auth()): Get[Out] =
  getV(name, relativePath, auth)

def get[Out: Spec](name: String, relativePath: String, auth: Auth): Get[Out] =
  getV(name, str(relativePath), auth)

// PATCH helpers
def patchV[In, Out: Spec](name: String, urlPath: BaseValue[String], body: BaseValue[In], auth: Auth = Auth()): Patch[Out] = {
  val absUrl: Value[String] = str(Env.BASE_URL.cel + ("/": Cel) + urlPath.cel)
  val patchArgs = PostRequest[In](
    url = absUrl,
    body = body,
    auth = auth
  )
  Call[PostRequest[In], PostResult[Out]](name, "http.patch", obj(patchArgs))
}

def patch[In, Out: Spec](name: String, relativePath: String, body: BaseValue[In], auth: Auth = Auth()): Patch[Out] =
  patchV(name, str(relativePath), body, auth)
