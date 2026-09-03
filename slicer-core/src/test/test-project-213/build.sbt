ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "spec"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbOptions += "-P:semanticdb:synthetics:on"

lazy val testProject = (project in file("."))
  .aggregate(macros, base, external, configured, entry)
  .settings(name := "test-project-213")

lazy val macros = (project in file("macros"))
  .settings(libraryDependencies += Dependencies.scalaReflect.value)

lazy val base = (project in file("base")).dependsOn(macros)

lazy val external = (project in file("external"))
  .dependsOn(base)
  .settings(libraryDependencies += Dependencies.cats)

lazy val configured = (project in file("configured"))
  .settings(
    addCompilerPlugin(Dependencies.kindProjector),
    libraryDependencies += Dependencies.sourcecode % Provided
  )

lazy val entry = (project in file("entry")).dependsOn(base, external)