/*
 * Copyright 2026 io.github.jbwheatley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package slicer.emit

import java.nio.file.{Files, Path}

import slicer.harness.Workspace
import slicer.model.*

class BuildFileWriterSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice-build-"), teardown = Workspace.delete)

  private val scalaVersion = "3.3.6"

  private val cats =
    Dependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, false)

  private val sourcecode =
    Dependency("com.lihaoyi", "sourcecode", "0.4.2", CrossVersion.Binary, DependencyScope.Provided, false)

  private val kindProjector =
    Dependency("org.typelevel", "kind-projector", "0.13.3", CrossVersion.Full, DependencyScope.Plugin, false)

  private val scalaJs = Platform.ScalaJs("1.19.0")

  workspace.test("a slice gets a build for every source root it touched") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt(
        scalaVersion = scalaVersion,
        sbtVersion = "2.0.0",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector("entry/src/main/scala", "base/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.sbt"))
    assert(build.contains(scalaVersion), build)
    assert(build.contains("""baseDirectory.value / "entry" / "src" / "main" / "scala""""), build)
    assert(Files.exists(out.resolve("project/build.properties")))
  }

  workspace.test("a mill slice gets a build listing every source root it touched") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill(
        scalaVersion = scalaVersion,
        millVersion = "1.0.5",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector("entry/src/main/scala", "base/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.mill"))
    assert(build.contains("object `package` extends ScalaModule"), build)
    assert(build.contains(s"""def scalaVersion = "${scalaVersion}""""), build)
    assert(build.contains("""Task.Sources("entry/src/main/scala", "base/src/main/scala")"""), build)
    assertEquals(Files.readString(out.resolve(".mill-version")).trim, "1.0.5")
  }

  workspace.test("a mill slice with no source roots falls back to the default source directory") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill(
        scalaVersion = scalaVersion,
        millVersion = "1.0.5",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector.empty,
      Vector.empty
    )
    assert(Files.readString(out.resolve("build.mill")).contains("""Task.Sources("src/main/scala")"""))
  }

  workspace.test("an sbt slice with no source roots falls back to the default source directory") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt(
        scalaVersion = scalaVersion,
        sbtVersion = "2.0.0",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector.empty,
      Vector.empty
    )
    assert(
      Files
        .readString(out.resolve("build.sbt"))
        .contains("""Seq(baseDirectory.value / "src" / "main" / "scala")"""),
      Files.readString(out.resolve("build.sbt"))
    )
  }

  workspace.test("the generated build carries the dependencies of the sliced project") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", Vector(cats), Vector.empty, Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.sbt"))
    assert(
      build.contains("""libraryDependencies ++= Seq(""" + "\n  \"org.typelevel\" %% \"cats-core\" % \"2.13.0\""),
      build
    )

    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("3.3.6", "0.12.0", Vector(cats), Vector.empty, Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    assert(Files.readString(out.resolve("build.mill")).contains("""mvn"org.typelevel::cats-core:2.13.0""""))
  }

  workspace.test("the generated build carries the scalac options of the sliced project") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", Vector.empty, Vector("-Xkind-projector", "-Xfatal-warnings"), Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val sbtBuild = Files.readString(out.resolve("build.sbt"))
    assert(sbtBuild.contains("""scalacOptions ++= Seq(""" + "\n  \"-Xkind-projector\"\n)"), sbtBuild)
    assert(!sbtBuild.contains("-Xfatal-warnings"), sbtBuild)

    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("3.3.6", "0.12.0", Vector.empty, Vector("-Xkind-projector", "-Werror"), Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val millBuild = Files.readString(out.resolve("build.mill"))
    assert(millBuild.contains("""def scalacOptions = Seq("-Xkind-projector")"""), millBuild)
    assert(!millBuild.contains("-Werror"), millBuild)
  }

  workspace.test("the generated build keeps each dependency in the scope it was declared in") { out =>
    val dependencies = Vector(cats, sourcecode, kindProjector)

    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", dependencies, Vector.empty, Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val sbtBuild = Files.readString(out.resolve("build.sbt"))
    assert(sbtBuild.contains(""""com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided"""), sbtBuild)
    assert(
      sbtBuild.contains(
        """compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))"""
      ),
      sbtBuild
    )

    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("3.3.6", "0.12.0", dependencies, Vector.empty, Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val millBuild = Files.readString(out.resolve("build.mill"))
    assert(millBuild.contains("""def mvnDeps = Seq(""" + "\n    mvn\"org.typelevel::cats-core:2.13.0\""), millBuild)
    assert(
      millBuild.contains("""def compileMvnDeps = Seq(""" + "\n    mvn\"com.lihaoyi::sourcecode:0.4.2\""),
      millBuild
    )
    assert(
      millBuild.contains(
        """def scalacPluginMvnDeps = Seq(""" + "\n    mvn\"org.typelevel:::kind-projector:0.13.3\""
      ),
      millBuild
    )
  }

  workspace.test("an sbt slice with macro sources gets a module compiled before the rest of the slice") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt(
        scalaVersion = scalaVersion,
        sbtVersion = "2.0.0",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector("base/src/main/scala"),
      Vector("macros/src/main/scala")
    )
    val build = Files.readString(out.resolve("build.sbt"))
    assert(build.contains("""lazy val macros = (project in file("macros"))"""), build)
    assert(build.contains(".dependsOn(macros)"), build)
    assert(build.contains("""baseDirectory.value / "src" / "main" / "scala""""), build)
  }

  workspace.test("a macro module carries the same dependencies and scalac options as the root it was split from") {
    out =>
      BuildFileWriter.writeBuildFiles(
        BuildTool.Sbt(scalaVersion, "2.0.0", Vector(cats, sourcecode), Vector("-Xkind-projector"), Platform.Jvm),
        out,
        Vector("base/src/main/scala"),
        Vector("macros/src/main/scala")
      )
      val sbtBuild = Files.readString(out.resolve("build.sbt"))
      val macroProject = sbtBuild.substring(sbtBuild.indexOf("lazy val macros"), sbtBuild.indexOf("lazy val root"))
      assert(macroProject.contains(""""org.typelevel" %% "cats-core" % "2.13.0""""), macroProject)
      assert(macroProject.contains(""""com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided"""), macroProject)
      assert(macroProject.contains(""""-Xkind-projector""""), macroProject)

      BuildFileWriter.writeBuildFiles(
        BuildTool.Mill(scalaVersion, "1.0.0", Vector(cats, sourcecode), Vector("-Xkind-projector"), Platform.Jvm),
        out,
        Vector("base/src/main/scala"),
        Vector("macros/src/main/scala")
      )
      val millBuild = Files.readString(out.resolve("build.mill"))
      val macroModule = millBuild.substring(millBuild.indexOf("object `macros`"))
      assert(macroModule.contains("""mvn"org.typelevel::cats-core:2.13.0""""), macroModule)
      assert(macroModule.contains("""mvn"com.lihaoyi::sourcecode:0.4.2""""), macroModule)
      assert(macroModule.contains("""def scalacOptions = Seq("-Xkind-projector")"""), macroModule)
  }

  workspace.test("an sbt slice of a Scala.js project builds on the platform it was sliced from") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "2.0.6", Vector(cats.copy(platformed = true)), Vector.empty, scalaJs),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.sbt"))
    assert(build.contains("enablePlugins(ScalaJSPlugin)"), build)
    assert(build.contains(""""org.typelevel" %% "cats-core" % "2.13.0""""), build)
    assertEquals(
      Files.readString(out.resolve("project/plugins.sbt")).trim,
      """addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.19.0")"""
    )
  }

  workspace.test("a slice of a Scala.js project built by sbt 1 spells the platform out for it") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", Vector(cats.copy(platformed = true)), Vector.empty, scalaJs),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.sbt"))
    assert(
      build.contains("""("org.typelevel" % "cats-core" % "2.13.0").cross(CrossVersion.binaryWith("sjs1_", ""))"""),
      build
    )
  }

  workspace.test("an sbt slice of a Scala Native project builds on the platform it was sliced from") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", Vector.empty, Vector.empty, Platform.ScalaNative("0.5.8")),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    assert(Files.readString(out.resolve("build.sbt")).contains("enablePlugins(ScalaNativePlugin)"))
    assertEquals(
      Files.readString(out.resolve("project/plugins.sbt")).trim,
      """addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.8")"""
    )
  }

  workspace.test("a JVM sbt slice is left without a plugins file to enable a platform with") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("3.3.6", "1.10.3", Vector.empty, Vector.empty, Platform.Jvm),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    assert(!Files.exists(out.resolve("project/plugins.sbt")))
    assert(!Files.readString(out.resolve("build.sbt")).contains("enablePlugins"))
  }

  workspace.test("a mill slice of a Scala.js project builds on the platform it was sliced from") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("3.3.6", "1.1.8", Vector(cats.copy(platformed = true)), Vector.empty, scalaJs),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.mill"))
    assert(build.contains("import mill.scalajslib.*"), build)
    assert(build.contains("object `package` extends ScalaJSModule"), build)
    assert(build.contains("""def scalaJSVersion = "1.19.0""""), build)
    assert(build.contains("""mvn"org.typelevel::cats-core::2.13.0""""), build)
  }

  workspace.test("a mill slice of a Scala Native project builds on the platform it was sliced from") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("3.3.6", "1.1.8", Vector.empty, Vector.empty, Platform.ScalaNative("0.5.8")),
      out,
      Vector("core/src/main/scala"),
      Vector.empty
    )
    val build = Files.readString(out.resolve("build.mill"))
    assert(build.contains("import mill.scalanativelib.*"), build)
    assert(build.contains("object `package` extends ScalaNativeModule"), build)
    assert(build.contains("""def scalaNativeVersion = "0.5.8""""), build)
  }

  workspace.test("the macro modules a Scala 2 slice is split into build on the same platform as the root") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Sbt("2.13.16", "2.0.0", Vector.empty, Vector.empty, scalaJs),
      out,
      Vector("base/src/main/scala"),
      Vector("macros/src/main/scala")
    )
    val sbtBuild = Files.readString(out.resolve("build.sbt"))
    assertEquals(sbtBuild.linesIterator.count(_.contains(".enablePlugins(ScalaJSPlugin)")), 2, sbtBuild)

    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill("2.13.16", "1.1.8", Vector.empty, Vector.empty, scalaJs),
      out,
      Vector("base/src/main/scala"),
      Vector("macros/src/main/scala")
    )
    val millBuild = Files.readString(out.resolve("build.mill"))
    assert(millBuild.contains("object `macros` extends ScalaJSModule"), millBuild)
    assertEquals(millBuild.linesIterator.count(_.contains("""def scalaJSVersion = "1.19.0"""")), 2, millBuild)
  }

  workspace.test("a mill slice with macro sources gets a module compiled before the rest of the slice") { out =>
    BuildFileWriter.writeBuildFiles(
      BuildTool.Mill(
        scalaVersion = scalaVersion,
        millVersion = "1.0.5",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      out,
      Vector("base/src/main/scala"),
      Vector("macros/src/main/scala")
    )
    val build = Files.readString(out.resolve("build.mill"))
    assert(build.contains("object `macros` extends ScalaModule"), build)
    assert(build.contains("def moduleDeps = Seq(`macros`)"), build)
    assert(build.contains("""Task.Sources(moduleDir / "src" / "main" / "scala")"""), build)
  }
}
