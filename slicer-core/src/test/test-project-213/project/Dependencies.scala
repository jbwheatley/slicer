import sbt.*
import sbt.Keys.*

object Dependencies {
  val catsVersion = "2.13.0"

  val cats: ModuleID = "org.typelevel" %% "cats-core" % catsVersion

  val sourcecode: ModuleID = "com.lihaoyi" %% "sourcecode" % "0.4.2"

  val kindProjector: ModuleID = ("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)

  val scalaReflect: Def.Initialize[ModuleID] = Def.setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)
}
