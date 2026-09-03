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

import java.nio.file.Path

import tui.viewport.TerminalSize

private[tui] sealed trait TuiMessage

private[tui] object TuiMessage {
  final case class TypeCharacter(character: Char) extends TuiMessage
  final case class MoveCaret(step: Int) extends TuiMessage
  final case class SliceFinished(outcome: Either[String, Path]) extends TuiMessage
  final case class SetTerminalSize(size: TerminalSize) extends TuiMessage
  case object DeleteBackward extends TuiMessage
  case object DeleteForward extends TuiMessage
  case object MoveUp extends TuiMessage
  case object MoveDown extends TuiMessage
  case object ShowOptions extends TuiMessage
  case object HideOptions extends TuiMessage
  case object ToggleOption extends TuiMessage
  final case class MoveChoice(step: Int) extends TuiMessage
  case object AskToConfirm extends TuiMessage
  case object AnswerConfirmation extends TuiMessage
  case object CancelSlice extends TuiMessage
  case object RunSlice extends TuiMessage
  case object DismissOutcome extends TuiMessage
}
