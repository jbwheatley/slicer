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

package tui.viewport

import java.io.File
import java.lang.ProcessBuilder.Redirect

import scala.io.Source
import scala.util.{Try, Using}

import cats.Eq

final case class TerminalSize(rows: Int, columns: Int)

object TerminalSize {

  given Eq[TerminalSize] = Eq.fromUniversalEquals

  val fallback: TerminalSize = TerminalSize(rows = 40, columns = 120)

  private val smallestRows: Int = 8
  private val smallestColumns: Int = 40

  def detectTerminalSize(): TerminalSize =
    Try {
      val process = ProcessBuilder("/bin/stty", "size")
        .redirectInput(File("/dev/tty"))
        .redirectError(Redirect.DISCARD)
        .start()
      val reported = Using.resource(Source.fromInputStream(process.getInputStream))(_.mkString).trim.split("\\s+")
      process.getOutputStream.close()
      process.waitFor()
      TerminalSize(rows = reported.head.toInt, columns = reported.last.toInt)
    }.toOption
      .collect { case size if size.rows > 0 && size.columns > 0 => clampToSmallest(size) }
      .getOrElse(fallback)

  def clampToSmallest(size: TerminalSize): TerminalSize = TerminalSize(
    rows = math.max(size.rows, smallestRows),
    columns = math.max(size.columns, smallestColumns)
  )
}
