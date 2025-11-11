// build.sbt
ThisBuild / scalaVersion := "3.3.1"

// Versioning via tags and stable version scheme (sbt-dynver)
ThisBuild / versionScheme := Some("early-semver")
// If your tags are NOT prefixed with "v", uncomment the following:
// ThisBuild / dynverTagPrefix := ""

name := "workflow-utils"
organization := "us.awfl"

// Maven Central metadata
ThisBuild / description := "Utilities and service clients for building AWFL workflows in Scala 3. Compact, dependency-light, and designed to be composed with the AWFL DSL."
ThisBuild / homepage := Some(url("https://github.com/awfl-us/workflow-utils"))
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/awfl-us/workflow-utils"),
    "scm:git:https://github.com/awfl-us/workflow-utils.git",
    Some("scm:git:ssh://git@github.com/awfl-us/workflow-utils.git")
  )
)
ThisBuild / developers := List(
  Developer(id = "awfl-us", name = "AWFL", email = "opensource@awfl.us", url = url("https://github.com/awfl-us"))
)
ThisBuild / pomIncludeRepository := { _ => false }
publishMavenStyle := true

// Dependencies
libraryDependencies ++= Seq(
  "us.awfl" %% "dsl" % "0.1.1"
)

scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
