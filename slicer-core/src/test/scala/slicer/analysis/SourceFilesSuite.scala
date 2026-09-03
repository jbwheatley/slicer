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

package slicer.analysis

import java.nio.file.{Files, Path}

import scala.util.Try

import slicer.harness.Workspace

class SourceFilesSuite extends munit.FunSuite {

  private val workspace = FunFixture[Path](
    setup = _ => Workspace.create("slice-source-files-"),
    teardown = Workspace.delete
  )

  workspace.test("every file under a directory is found, however deep") { root =>
    Files.createDirectories(root.resolve("a/b"))
    Files.writeString(root.resolve("a/Top.scala"), "object Top")
    Files.writeString(root.resolve("a/b/Deep.scala"), "object Deep")

    assertEquals(
      SourceFiles.listSourceFilesUnder(root).map(_.getFileName.toString).sorted,
      Vector("Deep.scala", "Top.scala")
    )
  }

  workspace.test("a directory symlinked into its own ancestor does not loop forever") { root =>
    val modules = Files.createDirectories(root.resolve("modules"))
    Files.writeString(modules.resolve("Module.scala"), "object Module")
    val looped = Try(Files.createSymbolicLink(modules.resolve("up"), root)).isSuccess
    assume(looped, "the filesystem refused a symlink")

    assertEquals(SourceFiles.listSourceFilesUnder(root).map(_.getFileName.toString), Vector("Module.scala"))
  }

  workspace.test("a symlinked source directory is still read through") { root =>
    val sources = Files.createDirectories(root.resolve("sources"))
    Files.writeString(sources.resolve("Linked.scala"), "object Linked")
    val project = Files.createDirectories(root.resolve("project"))
    val linked = Try(Files.createSymbolicLink(project.resolve("src"), sources)).isSuccess
    assume(linked, "the filesystem refused a symlink")

    assertEquals(SourceFiles.listSourceFilesUnder(project).map(_.getFileName.toString), Vector("Linked.scala"))
  }
}
