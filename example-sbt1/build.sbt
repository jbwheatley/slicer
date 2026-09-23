ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "example"
ThisBuild / resolvers += Resolver.defaultLocal

lazy val example = (project in file("."))
  .settings(
    name := "slicer-example-sbt1",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0"
  )
