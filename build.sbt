import org.typelevel.scalacoptions.ScalacOptions

val scala3 = "3.8.4"

val scalameta = "4.17.3"
val cats = "2.13.0"
val munit = "1.3.5"
val catsEffect = "3.7.1"
val layoutz = "0.8.0"
val mill = "1.1.8"

val checkCorpusSlices =
  taskKey[Unit]("Slice every definition of both sbt corpuses and compile each slice standalone")

val rejectSnapshotPublish = taskKey[Unit]("Fail the publish when the version did not come from a release tag")

val localVersion = "0.0.0-SNAPSHOT"

val taggedVersion = Def.setting {
  (ThisBuild / dynverGitDescribeOutput).value
    .filterNot(_.isSnapshot())
    .map(_.ref.value.stripPrefix("v"))
    .getOrElse(localVersion)
}

val writeSemanticClasspath =
  taskKey[File]("Write every module's test classpath to .claude/scala-semantic-classpath.txt for the MCP server")

val forksABuildTool = Tags.Tag("forksABuildTool")

inThisBuild(
  List(
    organization := "io.github.jbwheatley",
    homepage := Some(uri("https://github.com/jbwheatley/slicer")),
    developers := List(
      Developer(
        "jbwheatley",
        "jbwheatley",
        "jbwheatley@proton.me",
        uri("https://github.com/jbwheatley")
      )
    ),
    startYear := Some(2026),
    licenses := List("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")),
    scalaVersion := scala3,
    semanticdbEnabled := true,
    scalafixDependencies += "com.github.jatcwang" %% "scalafix-named-params" % "0.2.6",
    version := taggedVersion.value,
    concurrentRestrictions += Tags.limit(forksABuildTool, 1),
    rejectSnapshotPublish := {
      val versionInBuild = version.value
      if (versionInBuild.endsWith("-SNAPSHOT"))
        sys.error(s"failed to publish $versionInBuild - releases must be done via pushing a git tag")
    }
  )
)

val commonSettings = Seq(
  scalacOptions ++= Seq("-no-indent"),
  headerLicense := Some(HeaderLicense.ALv2("2026", "io.github.jbwheatley")),
  publish := publish.dependsOn(rejectSnapshotPublish).value,
  com.jsuereth.sbtpgp.PgpKeys.publishSigned :=
    com.jsuereth.sbtpgp.PgpKeys.publishSigned.dependsOn(rejectSnapshotPublish).value,
  Test / tpolecatExcludeOptions ++= Set(
    ScalacOptions.warnUnusedExplicits,
    ScalacOptions.warnUnusedParams
  )
)

lazy val suiteModules =
  List(slicerCore, tuiViewport, sbtTui, slicerSbt, slicerTui, slicerMill, corpusCheck)

lazy val root = (project in file("."))
  .enablePlugins(ScalaSemanticMcpPlugin)
  .aggregate((suiteModules :+ emittedBuildCheck).map(module => LocalProject(module.id)) *)
  .settings(commonSettings)
  .settings(
    name := "slicer",
    publish / skip := true,
    writeSemanticClasspath := Def.uncached {
      val converter = fileConverter.value
      val entries = Seq(
        (slicerCore / Test / fullClasspath).value,
        (slicerTui / Test / fullClasspath).value,
        (slicerMill / Test / fullClasspath).value,
        (slicerSbt / Test / fullClasspath).value,
        (sbtTui / Test / fullClasspath).value,
        (tuiViewport / Test / fullClasspath).value,
        (corpusCheck / Compile / fullClasspath).value,
        (emittedBuildCheck / Test / fullClasspath).value
      ).flatten.map(entry => converter.toPath(entry.data).toAbsolutePath.toString).distinct
      val listing = (ThisBuild / baseDirectory).value / ".claude" / "scala-semantic-classpath.txt"
      IO.write(listing, entries.mkString("\n") + "\n")
      listing
    }
  )

lazy val slicerCore = (project in file("slicer-core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "slicer-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % cats,
      "org.scalameta" %% "scalameta" % scalameta,
      "org.scalameta" %% "semanticdb-shared" % scalameta,
      "org.scalameta" %% "munit" % munit % Test
    ),
    Test / fork := true,
    Test / javaOptions ++= Seq("-Xmx2g", "-Xss4m"),
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "+l")
  )

lazy val tuiViewport = (project in file("tui-viewport"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "tui-viewport",
    libraryDependencies ++= Seq(
      "xyz.matthieucourt" %% "layoutz" % layoutz,
      "org.typelevel" %% "cats-core" % cats,
      "org.scalameta" %% "munit" % munit % Test
    )
  )

lazy val slicerTui = (project in file("slicer-tui"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(slicerCore % "compile->compile;test->test", tuiViewport)
  .settings(commonSettings)
  .settings(
    name := "slicer-tui",
    libraryDependencies ++= Seq(
      "xyz.matthieucourt" %% "layoutz" % layoutz,
      "org.scalameta" %% "munit" % munit % Test
    ),
    Test / fork := true,
    Test / javaOptions ++= Seq("-Xmx2g", "-Xss4m")
  )

lazy val slicerMill = (project in file("slicer-mill"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(slicerTui % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "slicer-mill",
    moduleName := "slicer-mill_mill1",
    conflictWarning := ConflictWarning.disable,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "mill-libs" % mill % Provided,
      "com.lihaoyi" %% "mill-testkit" % mill % Test,
      "org.scalameta" %% "munit" % munit % Test
    ),
    Test / fork := true,
    Test / javaOptions += {
      val converter = fileConverter.value
      val entries = (Compile / fullClasspath).value
        .map(entry => converter.toPath(entry.data).toAbsolutePath.toString)
        .filterNot(_.contains("com.lihaoyi"))
      s"-Dslicer.pluginClasspath=${entries.mkString(java.io.File.pathSeparator)}"
    },
    Test / javaOptions ++= Seq(
      "-Xss4m",
      s"-Dslicer.millCorpus=${(ThisBuild / baseDirectory).value / "slicer-core" / "src" / "test" / "test-project"}",
      s"-Dslicer.millCorpus213=${(ThisBuild / baseDirectory).value / "slicer-core" / "src" / "test" / "test-project-213"}",
      s"-Dslicer.unitProject=${(Test / resourceDirectory).value / "unit-test-project"}"
    ),
    Test / test / tags += forksABuildTool -> 1,
    Test / testOnly / tags += forksABuildTool -> 1
  )

lazy val emittedBuildCheck = (project in file("emitted-build-check"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(slicerCore % "test->test")
  .settings(commonSettings)
  .settings(
    name := "slicer-emitted-build-check",
    publish / skip := true,
    libraryDependencies += "org.scalameta" %% "munit" % munit % Test,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / javaOptions ++= Seq("-Xmx2g", "-Xss4m"),
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "+l"),
    Test / test / tags += forksABuildTool -> 1,
    Test / testOnly / tags += forksABuildTool -> 1
  )

lazy val corpusCheck = (project in file("corpus-check"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(slicerCore % "compile->test")
  .settings(commonSettings)
  .settings(
    name := "slicer-corpus-check",
    publish / skip := true,
    libraryDependencies += "org.typelevel" %% "cats-effect" % catsEffect,
    checkCorpusSlices := Def.uncached {
      val converter = fileConverter.value
      val classpath = (Compile / fullClasspath).value
        .map(entry => converter.toPath(entry.data).toAbsolutePath.toString)
        .mkString(java.io.File.pathSeparator)
      val options = ForkOptions()
        .withWorkingDirectory((ThisBuild / baseDirectory).value)
        .withRunJVMOptions(Vector("-Xmx2g", "-Xss4m"))
        .withOutputStrategy(StdoutOutput)
      val status = Fork.java(options, Seq("-cp", classpath, "slicer.corpus.CheckCorpusSlices"))
      if (status != 0) sys.error(s"failed to compile some slices of the corpuses")
    }
  )

lazy val sbtTui = (project in file("sbt-tui"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(tuiViewport)
  .settings(commonSettings)
  .settings(
    name := "sbt-tui",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "xyz.matthieucourt" %% "layoutz" % layoutz,
      "org.typelevel" %% "cats-core" % cats
    )
  )

lazy val slicerSbt = (project in file("slicer-sbt"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(slicerTui, sbtTui)
  .settings(commonSettings)
  .settings(
    name := "slicer-sbt",
    sbtPlugin := true,
    libraryDependencies += "org.scalameta" %% "munit" % munit % Test,
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "-Xmx2g",
      "-Xss4m",
      s"-Dslicer.sbtCorpus=${(ThisBuild / baseDirectory).value / "slicer-core" / "src" / "test" / "test-project"}"
    )
  )

addCommandAlias(
  "lintCheck",
  List(
    "scalafmtCheckAll",
    "scalafmtSbtCheck",
    "headerCheck",
    "scalafixAll --check"
  ).mkString(";", ";", "")
)

addCommandAlias(
  "testCheck",
  ("compile" :: suiteModules.map(module => s"${module.id}/testFull")).mkString(";", ";", "")
)

addCommandAlias("checkEmittedBuilds", List("compile", "emittedBuildCheck/testFull").mkString(";", ";", ""))

addCommandAlias("commitCheck", List("lintCheck", "testCheck", "checkEmittedBuilds").mkString(";", ";", ""))
