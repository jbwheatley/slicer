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

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import slicer.harness.Workspace

class WrittenSlicesSuite extends munit.FunSuite {

  private val out: FunFixture[Path] = FunFixture[Path](
    setup = _ => Workspace.create("slice-clear-"),
    teardown = Workspace.delete
  )

  private def writeSlice(out: Path, name: String): Path = {
    val target = out.resolve(name).resolve("src/main/scala/spec")
    Files.createDirectories(target)
    Files.writeString(target.resolve("Kept.scala"), "package spec\nobject Kept\n")
    Files.writeString(out.resolve(name).resolve("build.sbt"), "scalaVersion := \"3.8.4\"\n")
    out.resolve(name)
  }

  out.test("clearing removes every written slice and reports what went") { directory =>
    val first = writeSlice(directory, "spec-Kept-")
    val second = writeSlice(directory, "spec-Other-")

    val cleared = WrittenSlices.clearWrittenSlices(directory)

    assertEquals(cleared.map(_.size), Right(2))
    assert(cleared.exists(_.exists(_.contains(first.toString))), cleared.toString)
    assert(cleared.exists(_.exists(_.contains(second.toString))), cleared.toString)
    assert(!Files.exists(first))
    assert(!Files.exists(second))
  }

  out.test("clearing keeps the output directory itself") { directory =>
    writeSlice(directory, "spec-Kept-"): Unit

    assertEquals(WrittenSlices.clearWrittenSlices(directory).map(_.size), Right(1))
    assert(Files.isDirectory(directory))
  }

  out.test("clearing leaves loose files beside the slices alone") { directory =>
    val note = directory.resolve("notes.txt")
    Files.writeString(note, "mine\n")
    val written = writeSlice(directory, "spec-Kept-")

    val cleared = WrittenSlices.clearWrittenSlices(directory)

    assertEquals(cleared.map(_.size), Right(1))
    assert(cleared.exists(_.exists(_.contains(written.toString))), cleared.toString)
    assert(Files.exists(note))
  }

  test("clearing an output directory that was never written is not a failure") {
    val cleared = WrittenSlices.clearWrittenSlices(Path.of("/does/not/exist"))

    assertEquals(cleared.map(_.size), Right(1))
  }

  out.test("clearing an output directory that cannot be listed is reported, not thrown") { directory =>
    writeSlice(directory, "spec-Kept-"): Unit
    Files.setPosixFilePermissions(directory, Set.empty[PosixFilePermission].asJava): Unit
    try {
      assume(!Files.isReadable(directory), "the directory is still listable, so nothing can fail here")
      assert(WrittenSlices.clearWrittenSlices(directory).isLeft)
    } finally Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")): Unit
  }
}
