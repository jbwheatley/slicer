ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "spec"
ThisBuild / semanticdbEnabled := true

lazy val testProjectJs = (project in file("."))
  .aggregate(base, external)
  .settings(name := "test-project-js")

lazy val base = (project in file("base"))
  .enablePlugins(ScalaJSPlugin)

lazy val external = (project in file("external"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(base)
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0")
