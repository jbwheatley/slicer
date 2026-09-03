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

package slicer.tui.model

import slicer.model.{DefNode, SliceOptions}
import slicer.tui.SampleIndexes

import tui.viewport.TerminalSize

class TuiStateSuite extends munit.FunSuite {

  private val candidates: Vector[DefNode] =
    Vector(SampleIndexes.longDefinition, SampleIndexes.shortDefinition, SampleIndexes.longlyNamedDefinition)

  private val terminal = TerminalSize(rows = 24, columns = 100)

  private def blank: TuiState = TuiState.initial(candidates, terminal, "", SliceOptions.default)

  private def typed(text: String): TuiState =
    text.foldLeft(blank)((state, character) => state.typeCharacter(character, candidates))

  test("typing lands where the caret sits, not at the end") {
    val moved = typed("foo").moveCaret(-1).typeCharacter('X', candidates)
    assertEquals(moved.query, "foXo")
    assertEquals(moved.caret, 3)
  }

  test("backspace bites behind the caret and delete bites in front of it") {
    val behind = typed("foo").moveCaret(-1).deleteBackward(candidates)
    assertEquals(behind.query, "fo")
    assertEquals(behind.caret, 1)
    val ahead = typed("foo").moveCaret(-1).deleteForward(candidates)
    assertEquals(ahead.query, "fo")
    assertEquals(ahead.caret, 2)
  }

  test("the caret stays inside the query however hard the arrows are held") {
    val state = typed("foo")
    assertEquals(state.moveCaret(-9).caret, 0)
    assertEquals(state.moveCaret(9).caret, state.query.length)
  }

  test("deleting at either edge is a no-op rather than a crash") {
    val empty = blank
    assertEquals(empty.deleteBackward(candidates).query, "")
    assertEquals(empty.deleteForward(candidates).query, "")
    assertEquals(typed("f").moveCaret(9).deleteForward(candidates).query, "f")
  }

  test("a new query starts the cursor back at the top match") {
    val moved = blank.moveCursor(2)
    assertEquals(moved.cursor, 2)
    assertEquals(moved.typeCharacter('o', candidates).cursor, 0)
  }

  test("the match cursor wraps at both ends") {
    val state = blank
    assertEquals(state.moveCursor(-1).cursor, state.matches.size - 1)
    assertEquals(state.moveCursor(state.matches.size).cursor, 0)
  }

  test("the arrows steer the options once the panel is showing") {
    val panel = blank.showOptions
    assertEquals(panel.moveCursor(1).optionCursor, SliceOptionLabel.KeepFields)
    assertEquals(panel.moveCursor(1).cursor, panel.cursor)
    assertEquals(panel.moveCursor(-1).optionCursor, SliceOptionLabel.values.last)
  }

  test("toggling an option flips only the one under the cursor") {
    val panel = blank.showOptions
    assert(panel.sliceOptions.followImplementations)
    assert(!panel.sliceOptions.keepFields)
    val followingOff = panel.toggleOption
    assert(!followingOff.sliceOptions.followImplementations)
    assert(!followingOff.sliceOptions.keepFields)
    val fieldsOn = panel.moveCursor(1).toggleOption
    assert(fieldsOn.sliceOptions.followImplementations)
    assert(fieldsOn.sliceOptions.keepFields)
  }

  test("a query given up front is typed in, caret at its end") {
    val prefilled = TuiState.initial(candidates, terminal, "one", SliceOptions.default)
    assertEquals(prefilled.query, "one")
    assertEquals(prefilled.caret, 3)
    assertEquals(prefilled.matches, typed("one").matches)
  }

  test("a query that leaves one match opens on the confirmation") {
    val single = TuiState.initial(candidates, terminal, "soughtAfterMember", SliceOptions.default)
    assertEquals(single.matches.size, 1)
    assertEquals(single.screen, TuiScreen.Confirm)
    assertEquals(single.selected.map(_.displayName), Some("soughtAfterMember"))
  }

  test("a query that leaves several matches opens on the search") {
    val several = TuiState.initial(candidates, terminal, "e", SliceOptions.default)
    assert(several.matches.size > 1, several.matches.toString)
    assertEquals(several.screen, TuiScreen.Search)
  }

  test("options given up front are the options the slice runs with") {
    val lean =
      TuiState.initial(candidates, terminal, "", SliceOptions(followImplementations = false, keepFields = true))
    assertEquals(lean.sliceOptions, SliceOptions(followImplementations = false, keepFields = true))
  }

  test("leaving the options panel returns to the search") {
    val panel = blank.showOptions
    assertEquals(panel.screen, TuiScreen.Options)
    assertEquals(panel.hideOptions.screen, TuiScreen.Search)
  }
}
