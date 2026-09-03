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

import scala.concurrent.duration.*

import slicer.analysis.Reachability
import slicer.harness.*
import slicer.model.{BuildTool, SliceOptions}

import cats.syntax.eq.*

class EmittedSbtBuildSuite extends munit.FunSuite {

  override def munitTimeout: Duration = 2.minutes

  private val workspace = FunFixture[Path](
    setup = _ => Workspace.create("slice-sbt-build-"),
    teardown = Workspace.delete
  )

  private val options = SliceOptions.default

  private lazy val tool: BuildTool =
    TestProject.buildTool

  private lazy val sbt: Sbt = Sbt()

  workspace.test("the build emitted beside an sbt slice is compiled by sbt itself") { out =>
    val root = TestProject.resolve("spec.external.CallsLibrary.reducesWithLibraryGiven")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject.index,
      result = Reachability.computeSliceResult(TestProject.index, root, options),
      sourceRoot = TestProject.projectRoot,
      out = target,
      tool = tool
    ): Unit

    assert(Files.exists(target.resolve("build.sbt")), s"no sbt build in $target")
    assert(Files.exists(target.resolve("external/src/main/scala/spec/external/External.scala")))

    assertEquals(
      Files.readString(target.resolve("project/build.properties")).trim,
      Files.readString(TestProject.projectRoot.resolve("project/build.properties")).trim
    )

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains(""""external" / "src" / "main" / "scala""""), build)
    val declared = tool.dependencies
    assert(declared.exists(_.artifact === "cats-core"), declared.toString)
    declared.foreach(dependency =>
      assert(build.contains(dependency.renderSbtSyntax(tool.platform, platformAppliedByBuild = true)), build)
    )

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 slice is compiled by sbt itself") { out =>
    val scala2 = TestProject213.buildTool
    val root = TestProject213.resolve("spec.external.CallsLibrary.reducesWithLibraryImplicit")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject213.index,
      result = Reachability.computeSliceResult(TestProject213.index, root, options),
      sourceRoot = TestProject213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains(""""external" / "src" / "main" / "scala""""), build)
    assert(scala2.scalaVersion.startsWith("2.13."), scala2.scalaVersion)
    assert(build.contains(scala2.scalaVersion), build)
    scala2.dependencies.foreach(dependency =>
      assert(build.contains(dependency.renderSbtSyntax(scala2.platform, platformAppliedByBuild = true)), build)
    )

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }
  workspace.test("the build emitted beside a slice needing scalac options carries them") { out =>
    val root = TestProject.resolve("spec.configured.Configured.labelledMapper")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject.index,
      result = Reachability.computeSliceResult(TestProject.index, root, options),
      sourceRoot = TestProject.projectRoot,
      out = target,
      tool = tool
    ): Unit

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains("-Xkind-projector"), build)
    assert(build.contains(""""sourcecode""""), build)

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 slice needing a compiler plugin carries it") { out =>
    val scala2 = TestProject213.buildTool
    val root = TestProject213.resolve("spec.configured.Configured.labelledMapper")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject213.index,
      result = Reachability.computeSliceResult(TestProject213.index, root, options),
      sourceRoot = TestProject213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains("kind-projector"), build)
    assert(build.contains(""""sourcecode""""), build)

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("a slice reaching a Java class emits that class and sbt compiles it") { out =>
    val root = TestProject.resolve("spec.javacalls.CallsJava.describeRegistry")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject.index,
      result = Reachability.computeSliceResult(TestProject.index, root, options),
      sourceRoot = TestProject.projectRoot,
      out = target,
      tool = tool
    ): Unit

    assert(Files.exists(target.resolve("base/src/main/java/spec/javadefs/Registry.java")), s"no Java in $target")
    assert(
      Files.exists(target.resolve("base/src/main/java/spec/javadefs/tools/Formatter.java")),
      s"no import in $target"
    )
    assert(Files.exists(target.resolve("base/src/main/java/spec/javadefs/Marker.java")), s"no package mate in $target")

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala.js slice is compiled by sbt on Scala.js") { out =>
    val js = TestProjectJs.buildTool
    val root = TestProjectJs.resolve("spec.jsexternal.Summary.summarise")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectJs.index,
      result = Reachability.computeSliceResult(TestProjectJs.index, root, options),
      sourceRoot = TestProjectJs.projectRoot,
      out = target,
      tool = js
    ): Unit

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains("enablePlugins(ScalaJSPlugin)"), build)
    assertEquals(
      Files.readString(target.resolve("project/plugins.sbt")).trim,
      """addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")"""
    )
    assert(Files.exists(target.resolve("base/src/main/scala/spec/jsbase/Readings.scala")), s"no facade in $target")

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala Native slice is compiled by sbt on Scala Native") { out =>
    val native = TestProjectNative.buildTool
    val root = TestProjectNative.resolve("spec.nativeexternal.Summary.summarise")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectNative.index,
      result = Reachability.computeSliceResult(TestProjectNative.index, root, options),
      sourceRoot = TestProjectNative.projectRoot,
      out = target,
      tool = native
    ): Unit

    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains("enablePlugins(ScalaNativePlugin)"), build)
    assertEquals(
      Files.readString(target.resolve("project/plugins.sbt")).trim,
      """addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")"""
    )

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 3 macro slice is compiled by sbt itself") { out =>
    val root = TestProject.resolve("spec.macros.CallsMacros.callsReflectedLabel")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject.index,
      result = Reachability.computeSliceResult(TestProject.index, root, options),
      sourceRoot = TestProject.projectRoot,
      out = target,
      tool = tool
    ): Unit

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 macro slice compiles the macros in a module of their own") { out =>
    val scala2 = TestProject213.buildTool
    val root = TestProject213.resolve("spec.macros.CallsMacros.callsReflectedLabel")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProject213.index,
      result = Reachability.computeSliceResult(TestProject213.index, root, options),
      sourceRoot = TestProject213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    assert(
      Files.exists(target.resolve("macros/src/main/scala/spec/macros/MacroImplementations.scala")),
      s"the macro implementations are missing from $target"
    )
    val build = Files.readString(target.resolve("build.sbt"))
    assert(build.contains("""lazy val macros = (project in file("macros"))"""), build)
    assert(build.contains(".dependsOn(macros)"), build)

    val outcome = sbt.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Sbt.compiledClassesIn(target).nonEmpty, s"sbt reported success but compiled nothing in $target")
  }
}
