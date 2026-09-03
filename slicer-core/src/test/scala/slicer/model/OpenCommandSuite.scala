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

package slicer.model

import java.nio.file.{Path, Paths}

import slicer.harness.Workspace

import cats.syntax.eq.*

class OpenCommandSuite extends munit.FunSuite {

  private val directory = Paths.get("target/slice-open-commands").toAbsolutePath.normalize()

  private val spacedDirectory: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice open commands "), teardown = Workspace.delete)

  test("every suggested command names an editor and points at the directory") {
    val commands = OpenCommand.openCommandsForDirectory(directory)
    commands.foreach { open =>
      assert(open.editor.nonEmpty, open.toString)
      assert(open.command.contains(directory.toString), open.command)
    }
  }

  spacedDirectory.test("a directory whose path has spaces is quoted") { spaced =>
    OpenCommand
      .openCommandsForDirectory(spaced)
      .foreach(open => assert(open.command.contains(s"'${spaced.toString}'"), open.command))
  }

  test("the same directory always suggests the same commands") {
    assertEquals(OpenCommand.openCommandsForDirectory(directory), OpenCommand.openCommandsForDirectory(directory))
  }

  test("no command is suggested twice for the same editor") {
    val editors = OpenCommand.openCommandsForDirectory(directory).map(_.editor)
    assertEquals(editors.distinct.size, editors.size)
  }

  test("a relative directory is suggested as an absolute one") {
    val relative = Paths.get("target/slice-open-commands")
    OpenCommand
      .openCommandsForDirectory(relative)
      .foreach(open => assert(open.command.contains(relative.toAbsolutePath.normalize().toString), open.command))
  }

  test("either editors are offered, or a single file manager, never both") {
    val commands = OpenCommand.openCommandsForDirectory(directory)
    val fileManagers = commands.filter(_.editor === "Files")
    assert(fileManagers.isEmpty || commands.size === 1, commands.toString)
  }
}
