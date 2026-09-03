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

class EmitEditsSuite extends munit.FunSuite {

  private def cut(from: Int, to: Int): Emit.Edit = (from = from, to = to, replacement = "")

  test("edits are applied wherever they sit in the text") {
    assertEquals(Emit.applyEdits("abcdef", Vector(cut(2, 4))), "abef")
    assertEquals(Emit.applyEdits("abcdef", Vector(cut(4, 6), cut(0, 2))), "cd")
  }

  test("an insertion keeps the text around it") {
    val insert: Emit.Edit = (from = 3, to = 3, replacement = "-- ")
    assertEquals(Emit.applyEdits("abcdef", Vector(insert)), "abc-- def")
  }

  test("overlapping cuts are merged instead of cutting twice") {
    assertEquals(Emit.applyEdits("abcdefgh", Vector(cut(1, 4), cut(2, 6))), "agh")
    assertEquals(Emit.applyEdits("abcdefgh", Vector(cut(1, 6), cut(2, 3))), "agh")
  }

  test("an insertion at the end of a cut survives the merge") {
    val insert: Emit.Edit = (from = 4, to = 4, replacement = "X")
    assertEquals(Emit.applyEdits("abcdefgh", Vector(cut(1, 4), insert)), "aXefgh")
  }

  test("edits reaching past the text stop at its end") {
    assertEquals(Emit.applyEdits("abc", Vector(cut(1, 99))), "a")
    assertEquals(Emit.applyEdits("abc", Vector.empty), "abc")
  }
}
