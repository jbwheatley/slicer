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

private[render] object ShellLines {

  private val indent: String = "  "

  def wrapCommand(command: String, width: Int): Vector[String] = {
    val lines = splitIntoTokens(command).foldLeft(Vector.empty[String])((placed, token) =>
      appendToken(lines = placed, token = token, width = width)
    )
    lines.zipWithIndex.map { case (line, index) =>
      if (index === lines.size - 1) line else line + " \\"
    }
  }

  private def appendToken(lines: Vector[String], token: String, width: Int): Vector[String] = {
    val room = math.max(width - 2, 1)
    lines.lastOption match {
      case Some(line) if line.length + 1 + token.length <= room => lines.dropRight(1) :+ s"$line $token"
      case Some(_)                                              => lines :+ indent + token
      case None                                                 => Vector(token)
    }
  }

  private def splitIntoTokens(command: String): Vector[String] = {
    val (tokens, last, _) = command.foldLeft((Vector.empty[String], "", false)) {
      case ((collected, current, quoted), '\'') => (collected, current + '\'', !quoted)
      case ((collected, current, quoted), ' ') if !quoted =>
        (if (current.isEmpty) collected else collected :+ current, "", quoted)
      case ((collected, current, quoted), character) => (collected, current + character, quoted)
    }
    if (last.isEmpty) tokens else tokens :+ last
  }
}
