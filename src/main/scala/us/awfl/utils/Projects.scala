package us.awfl.utils

import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given

object Projects {
  case class CreateReq(name: Value[String], remote: Value[String])
  case class Project(id: Value[String])
  case class CreateResp(project: Project)

  def create(name: Value[String], remote: Value[String]): Post[CreateResp] =
    post("createProject", "projects/", obj(CreateReq(name, remote)))

  def delete(id: Value[String]): Post[Boolean] =
    us.awfl.utils.delete("deleteProject", str(("projects/": Cel) + id))
}
