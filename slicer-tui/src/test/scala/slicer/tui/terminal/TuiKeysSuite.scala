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

package slicer.tui.terminal

import slicer.tui.model.{TuiMessage, TuiScreen}

import layoutz.Key

class TuiKeysSuite extends munit.FunSuite {

  import TuiMessage._

  private def searching(key: Key): Option[TuiMessage] = TuiKeys.messageForKey(key, TuiScreen.Search)

  private def choosing(key: Key): Option[TuiMessage] = TuiKeys.messageForKey(key, TuiScreen.Options)

  private def confirming(key: Key): Option[TuiMessage] = TuiKeys.messageForKey(key, TuiScreen.Confirm)

  test("the side arrows move the caret rather than the focus") {
    assertEquals(searching(Key.Left), Some(MoveCaret(-1)))
    assertEquals(searching(Key.Right), Some(MoveCaret(1)))
  }

  test("the up and down arrows walk the matches") {
    assertEquals(searching(Key.Up), Some(MoveUp))
    assertEquals(searching(Key.Down), Some(MoveDown))
  }

  test("typing lands in the query, and both delete keys bite") {
    assertEquals(searching(Key.Char('q')), Some(TypeCharacter('q')))
    assertEquals(searching(Key.Char(' ')), Some(TypeCharacter(' ')))
    assertEquals(searching(Key.Backspace), Some(DeleteBackward))
    assertEquals(searching(Key.Delete), Some(DeleteForward))
  }

  test("enter asks to confirm rather than slicing straight away") {
    assertEquals(searching(Key.Enter), Some(AskToConfirm))
  }

  test("the confirmation picks yes or no with the up and down arrows and answers on enter") {
    assertEquals(confirming(Key.Up), Some(MoveChoice(-1)))
    assertEquals(confirming(Key.Down), Some(MoveChoice(1)))
    assertEquals(confirming(Key.Enter), Some(AnswerConfirmation))
    assertEquals(confirming(Key.Char('y')), None)
  }

  test("ctrl+o opens the options and takes you back out") {
    assertEquals(searching(Key.Ctrl('O')), Some(ShowOptions))
    assertEquals(choosing(Key.Ctrl('O')), Some(HideOptions))
    assertEquals(choosing(Key.Escape), Some(HideOptions))
  }

  test("space toggles an option, and typing never leaks into the panel") {
    assertEquals(choosing(Key.Char(' ')), Some(ToggleOption))
    assertEquals(choosing(Key.Enter), Some(ToggleOption))
    assertEquals(choosing(Key.Char('q')), None)
  }

  test("the options panel steers with up and down only") {
    assertEquals(choosing(Key.Up), Some(MoveUp))
    assertEquals(choosing(Key.Down), Some(MoveDown))
    assertEquals(choosing(Key.Left), None)
    assertEquals(choosing(Key.Right), None)
  }

  test("tab no longer cycles a focus that is gone") {
    assertEquals(searching(Key.Tab), None)
    assertEquals(choosing(Key.Tab), None)
  }
}
