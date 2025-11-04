// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "workflow-utils"
organization := "us.awfl"
version := "0.1.0-SNAPSHOT"

// Circe Core + Generic + Parser + YAML
libraryDependencies ++= Seq(
  "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"
)

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
