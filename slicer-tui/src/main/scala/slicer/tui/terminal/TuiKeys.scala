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

private[tui] object TuiKeys {

  import TuiMessage._

  def messageForKey(key: Key, screen: TuiScreen): Option[TuiMessage] = screen match {
    case TuiScreen.Options => optionsScreenMessage(key)
    case TuiScreen.Confirm => confirmationScreenMessage(key)
    case TuiScreen.Search  => searchScreenMessage(key)
    case TuiScreen.Done    => doneScreenMessage(key)
  }

  private def doneScreenMessage(key: Key): Option[TuiMessage] = key match {
    case Key.Enter | Key.Escape => Some(DismissOutcome)
    case _                      => None
  }

  private def optionsScreenMessage(key: Key): Option[TuiMessage] = key match {
    case Key.Char(' ') | Key.Enter  => Some(ToggleOption)
    case Key.Up                     => Some(MoveUp)
    case Key.Down                   => Some(MoveDown)
    case Key.Escape | Key.Ctrl('O') => Some(HideOptions)
    case _                          => None
  }

  private def confirmationScreenMessage(key: Key): Option[TuiMessage] = key match {
    case Key.Up    => Some(MoveChoice(-1))
    case Key.Down  => Some(MoveChoice(1))
    case Key.Enter => Some(AnswerConfirmation)
    case _         => None
  }

  private def searchScreenMessage(key: Key): Option[TuiMessage] = key match {
    case Key.Ctrl('O') => Some(ShowOptions)
    case Key.Char(c)   => Some(TypeCharacter(c))
    case Key.Backspace => Some(DeleteBackward)
    case Key.Delete    => Some(DeleteForward)
    case Key.Left      => Some(MoveCaret(-1))
    case Key.Right     => Some(MoveCaret(1))
    case Key.Up        => Some(MoveUp)
    case Key.Down      => Some(MoveDown)
    case Key.Enter     => Some(AskToConfirm)
    case _             => None
  }
}
