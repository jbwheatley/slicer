import sbt.*
import sbt.Keys.*

object Dependencies {
  val catsVersion = "2.13.0"

  val cats: ModuleID = "org.typelevel" %% "cats-core" % catsVersion

  val sourcecode: ModuleID = "com.lihaoyi" %% "sourcecode" % "0.4.2"
}
