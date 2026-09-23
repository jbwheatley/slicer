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

package slicer

import java.nio.file.{Files, Path}

import slicer.compat.Directories
import slicer.harness.Workspace

class DirectoriesSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice-file-tree-"), teardown = Workspace.delete)

  workspace.test("only the directories directly under one are listed, sorted, and files are not") { directory =>
    Files.createDirectories(directory.resolve("second/deeper")): Unit
    Files.createDirectories(directory.resolve("first")): Unit
    Files.writeString(directory.resolve("loose.txt"), "loose"): Unit

    assertEquals(
      Directories.listChildDirectories(directory).map(_.getFileName.toString),
      Vector("first", "second")
    )
  }

  workspace.test("a directory that is not there has no children rather than failing") { directory =>
    assertEquals(Directories.listChildDirectories(directory.resolve("never-written")), Vector.empty)
    assertEquals(Directories.listChildDirectories(Files.writeString(directory.resolve("file"), "text")), Vector.empty)
  }

  workspace.test("deleting a tree takes the files under it with it, and leaves its parent alone") { directory =>
    val tree = directory.resolve("tree")
    Files.createDirectories(tree.resolve("nested/deeper")): Unit
    Files.writeString(tree.resolve("nested/deeper/leaf.txt"), "leaf"): Unit

    Directories.deleteRecursively(tree)

    assert(!Files.exists(tree), s"$tree survived")
    assert(Files.isDirectory(directory), s"$directory should outlive what was under it")
  }

  workspace.test("deleting a tree that was never written is not a failure") { directory =>
    Directories.deleteRecursively(directory.resolve("never-written"))
  }
}
