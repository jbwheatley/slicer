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

package slicer.analysis

class LineIndexSuite extends munit.FunSuite {

  private val text = "package spec\n\nobject Top {\n  def run: Int = 1\n}\n"

  test("a line and column become the offset the character sits at") {
    val index = LineIndex(text)
    assertEquals(index.charOffset(0, 0), 0)
    assertEquals(text.charAt(index.charOffset(2, 7)), 'T')
    assertEquals(text.substring(index.charOffset(3, 2), index.charOffset(3, 5)), "def")
  }

  test("a line past the end of the text lands at the end of the text") {
    val index = LineIndex(text)
    assertEquals(index.charOffset(99, 0), text.length)
  }

  test("text without a trailing newline still indexes its last line") {
    val index = LineIndex("one\ntwo")
    assertEquals(index.charOffset(1, 0), 4)
  }
}
