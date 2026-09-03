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

class EmittedMillBuildSuite extends munit.FunSuite {

  override def munitTimeout: Duration = 2.minutes

  private val workspace = FunFixture[Path](
    setup = _ => Workspace.create("slice-mill-build-"),
    teardown = Workspace.delete
  )

  private val options = SliceOptions.default

  private lazy val tool: BuildTool =
    TestProjectMill.buildTool

  private lazy val mill: ScalaCompiler = Mill(TestProjectMill)

  workspace.test("the build emitted beside a mill slice is compiled by mill itself") { out =>
    val root = TestProjectMill.resolve("spec.external.CallsLibrary.reducesWithLibraryGiven")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill.index,
      result = Reachability.computeSliceResult(TestProjectMill.index, root, options),
      sourceRoot = TestProjectMill.projectRoot,
      out = target,
      tool = tool
    ): Unit

    assert(Files.exists(target.resolve("build.mill")), s"no mill build in $target")
    assert(Files.exists(target.resolve("external/src/main/scala/spec/external/External.scala")))

    assertEquals(
      Files.readString(target.resolve(".mill-version")).trim,
      Files.readString(TestProjectMill.projectRoot.resolve(".mill-version")).trim
    )

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("""Task.Sources("external/src/main/scala")"""), build)
    val declared = tool.dependencies
    assert(declared.exists(_.artifact === "cats-core"), declared.toString)
    declared.foreach(dependency => assert(build.contains(dependency.renderMillSyntax), build))

    val outcome = mill.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 slice is compiled by mill itself") { out =>
    val scala2 = TestProjectMill213.buildTool
    val root = TestProjectMill213.resolve("spec.external.CallsLibrary.reducesWithLibraryImplicit")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill213.index,
      result = Reachability.computeSliceResult(TestProjectMill213.index, root, options),
      sourceRoot = TestProjectMill213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("""Task.Sources("external/src/main/scala")"""), build)
    assert(scala2.scalaVersion.startsWith("2.13."), scala2.scalaVersion)
    assert(build.contains(scala2.scalaVersion), build)
    scala2.dependencies.foreach(dependency => assert(build.contains(dependency.renderMillSyntax), build))

    val outcome = Mill(TestProjectMill213).compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }
  workspace.test("the build emitted beside a slice needing scalac options carries them") { out =>
    val root = TestProjectMill.resolve("spec.configured.Configured.labelledMapper")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill.index,
      result = Reachability.computeSliceResult(TestProjectMill.index, root, options),
      sourceRoot = TestProjectMill.projectRoot,
      out = target,
      tool = tool
    ): Unit

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("-Xkind-projector"), build)
    assert(build.contains("sourcecode"), build)

    val outcome = mill.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 slice needing a compiler plugin carries it") { out =>
    val scala2 = TestProjectMill213.buildTool
    val root = TestProjectMill213.resolve("spec.configured.Configured.labelledMapper")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill213.index,
      result = Reachability.computeSliceResult(TestProjectMill213.index, root, options),
      sourceRoot = TestProjectMill213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("kind-projector"), build)
    assert(build.contains("sourcecode"), build)

    val outcome = Mill(TestProjectMill213).compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("a slice reaching a Java class emits that class and mill compiles it") { out =>
    val root = TestProjectMill.resolve("spec.javacalls.CallsJava.describeRegistry")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill.index,
      result = Reachability.computeSliceResult(TestProjectMill.index, root, options),
      sourceRoot = TestProjectMill.projectRoot,
      out = target,
      tool = tool
    ): Unit

    assert(Files.exists(target.resolve("base/src/main/java/spec/javadefs/Registry.java")), s"no Java in $target")
    assert(
      Files.exists(target.resolve("base/src/main/java/spec/javadefs/tools/Formatter.java")),
      s"no import in $target"
    )
    assert(Files.exists(target.resolve("base/src/main/java/spec/javadefs/Marker.java")), s"no package mate in $target")

    val outcome = mill.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala.js slice is compiled by mill on Scala.js") { out =>
    val js = TestProjectMillJs.buildTool
    val root = TestProjectMillJs.resolve("spec.jsexternal.Summary.summarise")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMillJs.index,
      result = Reachability.computeSliceResult(TestProjectMillJs.index, root, options),
      sourceRoot = TestProjectMillJs.projectRoot,
      out = target,
      tool = js
    ): Unit

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("object `package` extends ScalaJSModule"), build)
    assert(build.contains("""def scalaJSVersion = "1.22.0""""), build)
    assert(build.contains("""mvn"org.typelevel::cats-core::2.13.0""""), build)
    assert(Files.exists(target.resolve("base/src/main/scala/spec/jsbase/Readings.scala")), s"no facade in $target")

    val outcome = Mill(TestProjectMillJs).compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala Native slice is compiled by mill on Scala Native") { out =>
    val native = TestProjectMillNative.buildTool
    val root = TestProjectMillNative.resolve("spec.nativeexternal.Summary.summarise")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMillNative.index,
      result = Reachability.computeSliceResult(TestProjectMillNative.index, root, options),
      sourceRoot = TestProjectMillNative.projectRoot,
      out = target,
      tool = native
    ): Unit

    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("object `package` extends ScalaNativeModule"), build)
    assert(build.contains("""def scalaNativeVersion = "0.5.12""""), build)

    val outcome = Mill(TestProjectMillNative).compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 3 macro slice is compiled by mill itself") { out =>
    val root = TestProjectMill.resolve("spec.macros.CallsMacros.callsReflectedLabel")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill.index,
      result = Reachability.computeSliceResult(TestProjectMill.index, root, options),
      sourceRoot = TestProjectMill.projectRoot,
      out = target,
      tool = tool
    ): Unit

    val outcome = mill.compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }

  workspace.test("the build emitted beside a Scala 2 macro slice compiles the macros in a module of their own") { out =>
    val scala2 = TestProjectMill213.buildTool
    val root = TestProjectMill213.resolve("spec.macros.CallsMacros.callsReflectedLabel")
    val target = out.resolve(root.symbol.toDirectoryName)
    SliceWriter.writeSlice(
      index = TestProjectMill213.index,
      result = Reachability.computeSliceResult(TestProjectMill213.index, root, options),
      sourceRoot = TestProjectMill213.projectRoot,
      out = target,
      tool = scala2
    ): Unit

    assert(
      Files.exists(target.resolve("macros/src/main/scala/spec/macros/MacroImplementations.scala")),
      s"the macro implementations are missing from $target"
    )
    val build = Files.readString(target.resolve("build.mill"))
    assert(build.contains("object `macros` extends ScalaModule"), build)
    assert(build.contains("def moduleDeps = Seq(`macros`)"), build)
    assert(build.contains("""Task.Sources(moduleDir / "src" / "main" / "scala")"""), build)

    val outcome = Mill(TestProjectMill213).compileDirectory(target, Set.empty)
    assert(outcome.ok, outcome.errorReport)
    assert(Mill.compiledClassesIn(target).nonEmpty, s"mill reported success but compiled nothing in $target")
  }
}
