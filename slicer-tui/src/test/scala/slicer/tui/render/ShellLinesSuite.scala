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

package slicer.tui.render

import cats.syntax.eq.*

class ShellLinesSuite extends munit.FunSuite {

  private val command: String =
    "open -na '/Applications/IntelliJ IDEA.app' --args /home/someone/project/target/slice/com-example-Main"

  private def pasted(lines: Vector[String]): String =
    lines.map(line => line.stripSuffix(" \\").trim).mkString(" ")

  test("a command that fits stays on one line") {
    assertEquals(ShellLines.wrapCommand(command, 200), Vector(command))
  }

  test("a wrapped command pastes back as the command it came from") {
    Vector(30, 45, 60, 80).foreach { width =>
      val lines = ShellLines.wrapCommand(command, width)
      assert(lines.size > 1, s"width $width did not wrap: $lines")
      assertEquals(pasted(lines), command, s"width $width")
    }
  }

  test("a quoted path holding a space stays on one line") {
    val lines = ShellLines.wrapCommand(command, 60)
    assert(lines.exists(_.contains("'/Applications/IntelliJ IDEA.app'")), lines.toString)
  }

  test("a path wider than the panel is left on its own line rather than cut in half") {
    val path = "/home/someone/" + "deeply-nested/" * 12 + "slice"
    val lines = ShellLines.wrapCommand(s"code $path", 40)

    assert(lines.exists(line => line.trim === path), lines.toString)
    assertEquals(pasted(lines), s"code $path")
  }
}
