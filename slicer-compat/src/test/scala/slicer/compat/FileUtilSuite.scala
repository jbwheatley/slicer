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

package slicer.compat

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Collections

class FileUtilSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] = FunFixture[Path](
    setup = _ => Files.createTempDirectory("slice-file-util-"),
    teardown = directory => FileUtil.deleteRecursively(directory)
  )

  private def createDirectories(directory: Path): Unit = {
    val _ = Files.createDirectories(directory)
  }

  private def writeFile(file: Path, text: String): Unit = {
    val _ = Files.writeString(file, text)
  }

  private def setPermissions(directory: Path, permissions: java.util.Set[PosixFilePermission]): Unit = {
    val _ = Files.setPosixFilePermissions(directory, permissions)
  }

  private def writeSlice(out: Path, name: String): Path = {
    val target = out.resolve(name).resolve("src/main/scala/spec")
    createDirectories(target)
    writeFile(target.resolve("Kept.scala"), "package spec\nobject Kept\n")
    writeFile(out.resolve(name).resolve("build.sbt"), "scalaVersion := \"3.8.4\"\n")
    out.resolve(name)
  }

  workspace.test("only the directories directly under one are listed, sorted, and files are not") { directory =>
    createDirectories(directory.resolve("second/deeper"))
    createDirectories(directory.resolve("first"))
    writeFile(directory.resolve("loose.txt"), "loose")

    assertEquals(
      obtained = FileUtil.listChildDirectories(directory).map(_.getFileName.toString),
      expected = Vector("first", "second")
    )
  }

  workspace.test("a directory that is not there has no children rather than failing") { directory =>
    assertEquals(
      obtained = FileUtil.listChildDirectories(directory.resolve("never-written")),
      expected = Vector.empty[Path]
    )
    assertEquals(
      obtained = FileUtil.listChildDirectories(Files.writeString(directory.resolve("file"), "text")),
      expected = Vector.empty[Path]
    )
  }

  workspace.test("deleting a tree takes the files under it with it, and leaves its parent alone") { directory =>
    val tree = directory.resolve("tree")
    createDirectories(tree.resolve("nested/deeper"))
    writeFile(tree.resolve("nested/deeper/leaf.txt"), "leaf")

    FileUtil.deleteRecursively(tree)

    assert(!Files.exists(tree), s"$tree survived")
    assert(Files.isDirectory(directory), s"$directory should outlive what was under it")
  }

  workspace.test("deleting a tree that was never written is not a failure") { directory =>
    FileUtil.deleteRecursively(directory.resolve("never-written"))
  }

  workspace.test("clearing removes every written slice and reports what went") { directory =>
    val first = writeSlice(directory, "spec-Kept-")
    val second = writeSlice(directory, "spec-Other-")

    val cleared = FileUtil.clearWrittenSlices(directory)

    assertEquals(obtained = cleared.map(_.size), expected = Right(2))
    assert(cleared.exists(_.exists(_.contains(first.toString))), cleared.toString)
    assert(cleared.exists(_.exists(_.contains(second.toString))), cleared.toString)
    assert(!Files.exists(first))
    assert(!Files.exists(second))
  }

  workspace.test("clearing keeps the output directory itself") { directory =>
    val _ = writeSlice(directory, "spec-Kept-")

    assertEquals(obtained = FileUtil.clearWrittenSlices(directory).map(_.size), expected = Right(1))
    assert(Files.isDirectory(directory))
  }

  workspace.test("clearing leaves loose files beside the slices alone") { directory =>
    val note = directory.resolve("notes.txt")
    writeFile(note, "mine\n")
    val written = writeSlice(directory, "spec-Kept-")

    val cleared = FileUtil.clearWrittenSlices(directory)

    assertEquals(obtained = cleared.map(_.size), expected = Right(1))
    assert(cleared.exists(_.exists(_.contains(written.toString))), cleared.toString)
    assert(Files.exists(note))
  }

  test("clearing an output directory that was never written is not a failure") {
    val cleared = FileUtil.clearWrittenSlices(Path.of("/does/not/exist"))

    assertEquals(obtained = cleared.map(_.size), expected = Right(1))
  }

  workspace.test("clearing an output directory that cannot be listed is reported, not thrown") { directory =>
    val _ = writeSlice(directory, "spec-Kept-")
    setPermissions(directory, Collections.emptySet[PosixFilePermission]())
    try {
      assume(!Files.isReadable(directory), "the directory is still listable, so nothing can fail here")
      assert(FileUtil.clearWrittenSlices(directory).isLeft)
    } finally setPermissions(directory, PosixFilePermissions.fromString("rwx------"))
  }
}
