package us.awfl.services

import us.awfl.dsl._
import us.awfl.dsl.auto.given
import us.awfl.dsl.Cel._
import us.awfl.dsl.CelOps._
import io.circe.generic.auto._

object Storage {

  case class ReadFileArgs(bucket: Value[String], `object`: Value[String], alt: Value[String] = str("media"))
  // case class FileContent(content: Value[String])

  /**
   * Reads the content of a file from a Google Cloud Storage bucket.
   * @param name Workflow step name.
   * @param bucket Bucket name.
   * @param object Path to object within the bucket.
   */
  // Consider keeping object as simple String to prevent injection exploits
  def readFile(name: String, bucket: Value[String], `object`: String): Step[String, Value[String]] = {
    val args = obj(ReadFileArgs(bucket, str(CelFunc("text.url_encode", `object`))))
    val buildArgs = Try(s"${name}_buildArgs", List() -> args)
    val call: Step[String, Value[String]] = Call(name, "googleapis.storage.v1.objects.get", args)
    val maybeDecode = Switch("maybeDecode", List(
      (CelFunc("get_type", call.resultValue) === "bytes") -> (List() -> str(CelFunc("text.decode", call.resultValue))),
      (true: Cel) -> (List() -> call.resultValue)
    ))
    Block(s"${name}_block", List[Step[?, ?]](
      buildArgs,
      Log(s"${name}_logFileReadCall",
        str(("Storage readFile call: ": Cel) + CelFunc("json.encode_to_string", buildArgs.resultValue))
      ),
      call,
      maybeDecode
    ) -> maybeDecode.resultValue)
  }
}
