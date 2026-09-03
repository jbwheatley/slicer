ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "example"

lazy val example = (project in file("."))
  .settings(
    name := "slicer-example",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0"
  )
