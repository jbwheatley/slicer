ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "spec"
ThisBuild / semanticdbEnabled := true

lazy val testProject = (project in file("."))
  .aggregate(macros, base, external, configured, entry)
  .settings(name := "test-project")

lazy val macros = (project in file("macros"))

lazy val base = (project in file("base")).dependsOn(macros)

lazy val external = (project in file("external"))
  .dependsOn(base)
  .settings(libraryDependencies += Dependencies.cats)

lazy val configured = (project in file("configured"))
  .settings(
    scalacOptions += "-Xkind-projector",
    libraryDependencies += Dependencies.sourcecode % Provided
  )

lazy val entry = (project in file("entry")).dependsOn(base, external)
