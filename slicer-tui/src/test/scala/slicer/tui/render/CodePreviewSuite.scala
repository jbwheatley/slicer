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

import slicer.tui.SampleIndexes

import cats.syntax.eq.*

class CodePreviewSuite extends munit.FunSuite {

  private val index = SampleIndexes.withLongSource

  private def preview(maxLines: Int, maxWidth: Int): Vector[String] =
    CodePreview.renderCodePreview(
      index = index,
      node = SampleIndexes.shortDefinition,
      maxLines = maxLines,
      maxWidth = maxWidth
    )

  test("the preview numbers the lines it shows and pads them to the width") {
    val lines = preview(maxLines = 10, maxWidth = 40)
    assertEquals(lines.size, 1)
    assert(TextFit.stripStyling(lines.head).startsWith("   1  "), lines.head)
    assertEquals(TextFit.stripStyling(lines.head).length, 40)
  }

  test("the definition's own name is highlighted on its first line") {
    val plain = preview(maxLines = 10, maxWidth = 40).head
    assert(plain.contains(SampleIndexes.shortDefinition.displayName), plain)
    assert(TextFit.stripStyling(plain).length <= plain.length, plain)
  }

  test("a long definition is cut off with a count of what is left") {
    val lines = CodePreview.renderCodePreview(
      index = index,
      node = SampleIndexes.longDefinition,
      maxLines = 6,
      maxWidth = 60
    )
    assertEquals(lines.size, 6)
    assert(lines.last.trim.startsWith("..."), lines.last)
    assert(lines.last.contains("more lines"), lines.last)
  }

  test("every line is cut to the width it was given") {
    val lines =
      CodePreview.renderCodePreview(index = index, node = SampleIndexes.longDefinition, maxLines = 4, maxWidth = 20)
    assertEquals(lines.map(line => TextFit.stripStyling(line).length).distinct, Vector(20))
    assert(lines.exists(_.endsWith("…")), lines.mkString("\n"))
  }

  test("a definition whose source is missing says so instead of failing") {
    val elsewhere = SampleIndexes.longDefinition.copy(file = java.nio.file.Paths.get("/nowhere/Absent.scala"))
    val lines = CodePreview.renderCodePreview(index = index, node = elsewhere, maxLines = 5, maxWidth = 30)
    assert(lines.forall(_ === "source unavailable"), lines.mkString("\n"))
  }
}
