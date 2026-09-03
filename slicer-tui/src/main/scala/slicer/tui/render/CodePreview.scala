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

import slicer.analysis.Index
import slicer.model.DefNode

import cats.syntax.eq.*
import layoutz.*

private[render] object CodePreview {

  def renderCodePreview(index: Index, node: DefNode, maxLines: Int, maxWidth: Int): Vector[String] =
    index.sources.get(node.file) match {
      case None => Vector("source unavailable")
      case Some(text) =>
        val from = text.lastIndexOf('\n', math.max(node.start - 1, 0)) + 1
        val to = text.indexOf('\n', math.max(node.end - 1, 0)) match {
          case -1  => text.length
          case end => end
        }
        val firstLine = text.take(from).count(_ === '\n') + 1
        val body = text.substring(from, math.max(to, from)).split("\n", -1).toVector
        val shown = if (body.size <= maxLines) body.size else math.max(maxLines - 1, 0)
        val numbered = body.zipWithIndex.take(shown).map { case (line, offset) =>
          val gutter = (firstLine + offset).toString.reverse.padTo(4, ' ').reverse
          TextFit.padToWidth(s"$gutter  $line", maxWidth)
        }
        val highlighted = numbered.zipWithIndex.map { case (line, offset) =>
          if (offset === 0) highlightName(line, node.displayName) else line
        }
        val hidden = body.size - shown
        if (hidden <= 0) highlighted else highlighted :+ TextFit.padToWidth(s"      ... $hidden more lines", maxWidth)
    }

  private def highlightName(line: String, name: String): String =
    line.indexOf(name) match {
      case -1 => line
      case at =>
        val ends = at + name.length
        line.take(at) + Text(name).style(Style.Reverse ++ Style.Bold).render + line.substring(ends)
    }
}
