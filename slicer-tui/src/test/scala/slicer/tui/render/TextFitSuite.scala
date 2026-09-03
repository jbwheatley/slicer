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

import tui.viewport.TerminalEscapes

class TextFitSuite extends munit.FunSuite {

  test("a line shorter than the width is padded out to it") {
    assertEquals(TextFit.padToWidth("abc", 6), "abc   ")
    assertEquals(TextFit.padToWidth("abcdef", 6), "abcdef")
  }

  test("a line longer than the width loses its tail to an ellipsis, and still fits") {
    assertEquals(TextFit.padToWidth("abcdefgh", 6), "abcde…")
    assertEquals(TextFit.padToWidth("abcdefgh", 6).length, 6)
    assertEquals(TextFit.padToWidth("abcdefgh", 0), "…")
  }

  test("keeping the tail drops the head instead, and still fits") {
    assertEquals(TextFit.truncateKeepingTail("abcdefgh", 4), "…fgh")
    assertEquals(TextFit.truncateKeepingTail("abcdefgh", 4).length, 4)
    assertEquals(TextFit.truncateKeepingTail("abc", 4), "abc")
    assertEquals(TextFit.truncateKeepingTail("abcdefgh", 0), "…")
  }

  test("styling is stripped so a styled line is measured by what it shows") {
    val styled = s"${27.toChar}[31mred${27.toChar}[0m"
    assertEquals(TextFit.stripStyling(styled), "red")
    assertEquals(TextFit.stripStyling("plain"), "plain")
  }

  test("only styling is stripped, never the codes that move the cursor") {
    assertEquals(TextFit.stripStyling(TerminalEscapes.topLeftCorner + "text"), TerminalEscapes.topLeftCorner + "text")
  }
}
