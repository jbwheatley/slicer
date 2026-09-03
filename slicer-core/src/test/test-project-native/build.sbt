ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "spec"
ThisBuild / semanticdbEnabled := true

lazy val testProjectNative = (project in file("."))
  .aggregate(base, external)
  .settings(name := "test-project-native")

lazy val base = (project in file("base"))
  .enablePlugins(ScalaNativePlugin)

lazy val external = (project in file("external"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(base)
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0")
