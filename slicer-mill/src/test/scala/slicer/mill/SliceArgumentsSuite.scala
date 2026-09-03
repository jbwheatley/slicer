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

package slicer.mill

import java.nio.file.Paths

import slicer.model.*

// scalafix:off DisableSyntax.defaultArgs
class SliceArgumentsSuite extends munit.FunSuite {

  private val sourceRoot = Paths.get("/work/project")
  private val out = Paths.get("/work/project/out/slice")
  private val semanticdbDirs =
    Vector(Paths.get("/work/project/out/base/semanticdb"), Paths.get("/work/project/out/app"))
  private val sourceDirs = Vector(Paths.get("/work/project/base/src"), Paths.get("/work/project/app/src"))

  private val tool = BuildTool.Mill(
    scalaVersion = "3.3.6",
    millVersion = "1.1.8",
    dependencies = Vector(
      Dependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, false),
      Dependency("com.google.guava", "guava", "33.4.0-jre", CrossVersion.Disabled, DependencyScope.Compile, false),
      Dependency("com.lihaoyi", "sourcecode", "0.4.2", CrossVersion.Binary, DependencyScope.Provided, false),
      Dependency("org.typelevel", "kind-projector", "0.13.3", CrossVersion.Full, DependencyScope.Plugin, false)
    ),
    scalacOptions = Vector("-Xkind-projector", "-source:future"),
    platform = Platform.Jvm
  )

  private val query = "spec.external.CallsLibrary"
  private val options = SliceOptions(followImplementations = false, keepFields = true)

  private def render(
      tool: BuildTool.Mill = tool,
      query: String = query,
      options: SliceOptions = options
  ): Vector[String] =
    SliceArguments.renderAsArgs(
      sourceRoot = sourceRoot,
      out = out,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      tool = tool,
      query = query,
      options = options
    )

  private def fieldsOf(args: Vector[String]): SliceArguments.Fields =
    SliceArguments.toFields(args).fold(failure => fail(failure.toString), identity)

  test("the paths a request carries survive being handed over as arguments") {
    val fields = fieldsOf(render())

    assertEquals(SliceArguments.readSourceRoot(fields), Right(sourceRoot))
    assertEquals(SliceArguments.readOut(fields), Right(out))
    assertEquals(SliceArguments.readSemanticdbDirs(fields), Right(semanticdbDirs))
    assertEquals(SliceArguments.readSourceDirs(fields), Right(sourceDirs))
  }

  test("the build tool a request carries survives being handed over as arguments") {
    assertEquals(SliceArguments.readBuildTool(fieldsOf(render())), Right(tool))
  }

  test("the query and options a request carries survive being handed over as arguments") {
    val fields = fieldsOf(render())

    assertEquals(SliceArguments.readQuery(fields), query)
    assertEquals(SliceArguments.readOptions(fields), Right(options))
  }

  test("a request carries the platform the module it came from builds on") {
    val onScalaJs = tool.copy(
      platform = Platform.ScalaJs("1.19.0"),
      dependencies = tool.dependencies.map(_.copy(platformed = true))
    )

    assertEquals(SliceArguments.readBuildTool(fieldsOf(render(tool = onScalaJs))), Right(onScalaJs))
  }

  test("a request with no query and the default options reads back with them") {
    val fields = fieldsOf(render(query = "", options = SliceOptions.default))

    assertEquals(SliceArguments.readQuery(fields), "")
    assertEquals(SliceArguments.readOptions(fields), Right(SliceOptions.default))
  }

  test("an argument without a key is reported rather than ignored") {
    assertEquals(
      SliceArguments.toFields(render() :+ "just-an-argument"),
      Left(SliceFailure("slice request has arguments without a key: just-an-argument"))
    )
  }

  test("a request missing the source root is reported") {
    val without = fieldsOf(render().filterNot(_.startsWith("source-root=")))

    assertEquals(SliceArguments.readSourceRoot(without), Left(SliceFailure("slice request carries no source-root")))
  }

  test("a request with an unreadable option flag is reported rather than thrown") {
    val garbled = fieldsOf(render().map(_.replace("keep-fields=true", "keep-fields=maybe")))

    assertEquals(
      SliceArguments.readOptions(garbled),
      Left(SliceFailure("slice request has an unreadable keep-fields: maybe"))
    )
  }

  test("a request with an unreadable dependency is reported rather than thrown") {
    val garbled = fieldsOf(render().map(_.replace("|Compile|false", "|Runtime|false")))

    assert(SliceArguments.readBuildTool(garbled).isLeft, garbled)
  }

  test("a scalac option carrying a newline survives being handed over as an argument") {
    val awkward = tool.copy(scalacOptions = Vector("-Xmacro-settings:first\nsecond"))

    assertEquals(SliceArguments.readBuildTool(fieldsOf(render(tool = awkward))), Right(awkward))
  }
}
