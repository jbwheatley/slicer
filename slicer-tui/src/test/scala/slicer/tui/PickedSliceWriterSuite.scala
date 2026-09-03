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

package slicer.tui

import java.nio.file.{Files, Path}

import slicer.harness.{TestProject, Workspace}
import slicer.model.SliceOptions

class PickedSliceWriterSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice-picked-"), teardown = Workspace.delete)

  workspace.test("the picked definition is written to a directory of its own, named after it") { out =>
    val root = TestProject.resolve("spec.entry.Handler.handlesWithOneParameter")
    val target = PickedSliceWriter.writeSlice(
      index = TestProject.index,
      root = root,
      options = SliceOptions.default,
      sourceRoot = TestProject.projectRoot,
      out = out,
      tool = TestProject.buildTool
    )

    assertEquals(target, out.resolve(root.symbol.toDirectoryName))
    assert(Files.exists(target.resolve("entry/src/main/scala/spec/entry/Handler.scala")), target.toString)
    assert(Files.exists(target.resolve("build.sbt")), target.toString)
  }

  workspace.test("two picked definitions land beside each other rather than on top of each other") { out =>
    val options = SliceOptions.default
    val first = TestProject.resolve("spec.entry.Handler.handlesWithOneParameter")
    val second = TestProject.resolve("spec.entry.Handler.handlesOpaqueType")

    val one =
      PickedSliceWriter.writeSlice(
        TestProject.index,
        first,
        options,
        TestProject.projectRoot,
        out,
        TestProject.buildTool
      )
    val other =
      PickedSliceWriter.writeSlice(
        TestProject.index,
        second,
        options,
        TestProject.projectRoot,
        out,
        TestProject.buildTool
      )

    assertNotEquals(one, other)
    assert(Files.isDirectory(one), one.toString)
    assert(Files.isDirectory(other), other.toString)
  }
}
