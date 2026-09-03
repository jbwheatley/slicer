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

class TerminalSizeSuite extends munit.FunSuite {

  test("the fallback size is big enough to draw both panes") {
    assert(TerminalSize.fallback.rows >= 8, TerminalSize.fallback.toString)
    assert(TerminalSize.fallback.columns >= 40, TerminalSize.fallback.toString)
  }

  test("a detected size is never smaller than the smallest drawable frame") {
    val detected = TerminalSize.detectTerminalSize()
    assert(detected.rows >= 8, detected.toString)
    assert(detected.columns >= 40, detected.toString)
  }

  test("detecting twice in a row reports the same size") {
    assertEquals(TerminalSize.detectTerminalSize(), TerminalSize.detectTerminalSize())
  }
}
