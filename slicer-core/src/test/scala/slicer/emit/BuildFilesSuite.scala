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
import slicer.model.{BuildTool, Platform}

class BuildFilesSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice-build-files-"), teardown = Workspace.delete)

  private val tools: Vector[BuildTool] = Vector(
    BuildTool.Sbt(
      scalaVersion = "3.3.6",
      sbtVersion = "2.0.0",
      dependencies = Vector.empty,
      scalacOptions = Vector.empty,
      platform = Platform.Jvm
    ),
    BuildTool.Mill(
      scalaVersion = "3.3.6",
      millVersion = "1.0.5",
      dependencies = Vector.empty,
      scalacOptions = Vector.empty,
      platform = Platform.Jvm
    )
  )

  private val quoted = """"([^"]*)"""".r

  private def collectSourceRoots(out: Path, tool: BuildTool): Vector[String] = tool match {
    case _: BuildTool.Sbt =>
      Files
        .readString(out.resolve("build.sbt"))
        .linesIterator
        .flatMap(_.split("baseDirectory.value").drop(1))
        .map(fragment => quoted.findAllMatchIn(fragment).map(_.group(1)).mkString("/"))
        .toVector
    case _: BuildTool.Mill =>
      val line = Files
        .readString(out.resolve("build.mill"))
        .linesIterator
        .find(_.contains("Task.Sources"))
        .getOrElse(fail(s"no source roots in the build written to $out"))
      quoted.findAllMatchIn(line).map(_.group(1)).toVector
  }

  tools.foreach { tool =>

    workspace.test(s"every source root a sliced file sits under reaches the ${tool.name} build, once and in order") {
      out =>
        Emit.writeBuildFiles(
          out,
          Vector(
            out.resolve("entry/src/main/scala/spec/entry/Handler.scala"),
            out.resolve("base/src/main/scala/spec/base/Types.scala"),
            out.resolve("base/src/main/scala/spec/base/deep/Nested.scala")
          ),
          Set.empty,
          tool
        )
        assertEquals(collectSourceRoots(out, tool), Vector("base/src/main/scala", "entry/src/main/scala"))
    }

    workspace.test(s"only the newest cross-built root of a module reaches the ${tool.name} build") { out =>
      Emit.writeBuildFiles(
        out,
        Vector(
          out.resolve("base/src/main/scala/spec/base/Types.scala"),
          out.resolve("base/src/main/scala-2.13/spec/base/Compat.scala"),
          out.resolve("base/src/main/scala-3/spec/base/Compat.scala")
        ),
        Set.empty,
        tool
      )
      assertEquals(collectSourceRoots(out, tool), Vector("base/src/main/scala", "base/src/main/scala-3"))
    }

    workspace.test(s"a sliced file under no recognisable source root leaves the ${tool.name} build on its default") {
      out =>
        Emit.writeBuildFiles(out, Vector(out.resolve("loose/Stray.scala")), Set.empty, tool)
        assertEquals(collectSourceRoots(out, tool), Vector("src/main/scala"))
    }

    workspace.test(s"a source root at the top of the slice reaches the ${tool.name} build without a module in front") {
      out =>
        Emit.writeBuildFiles(out, Vector(out.resolve("src/main/scala/spec/Only.scala")), Set.empty, tool)
        assertEquals(collectSourceRoots(out, tool), Vector("src/main/scala"))
    }
  }
}
