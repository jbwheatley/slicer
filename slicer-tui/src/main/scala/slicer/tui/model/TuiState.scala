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

import slicer.model.{DefNode, SliceOptions}
import slicer.tui.model
import slicer.tui.search.FuzzySearch

import cats.Eq
import cats.syntax.eq.*
import tui.viewport.TerminalSize

private[tui] final case class TuiState(
    query: String,
    caret: Int,
    matches: Vector[DefNode],
    cursor: Int,
    screen: TuiScreen,
    optionCursor: SliceOptionLabel,
    answer: ConfirmationAnswer,
    enabledOptions: Set[SliceOptionLabel],
    running: Boolean,
    outcome: Option[Either[String, Path]],
    terminal: TerminalSize
) {

  lazy val selected: Option[DefNode] = matches.lift(cursor)

  lazy val sliceOptions: SliceOptions =
    SliceOptionLabel.values.foldLeft(SliceOptions.default) { (options, label) =>
      label.applyToOptions(options, enabledOptions.contains(label))
    }

  def withQuery(value: String, candidates: Vector[DefNode]): TuiState =
    copy(
      query = value,
      caret = math.min(caret, value.length),
      matches = FuzzySearch.rankCandidates(value, candidates),
      cursor = 0
    )

  def typeCharacter(character: Char, candidates: Vector[DefNode]): TuiState =
    copy(caret = caret + 1).withQuery(query.take(caret) + character + query.drop(caret), candidates)

  def deleteBackward(candidates: Vector[DefNode]): TuiState =
    if (caret === 0) this
    else copy(caret = caret - 1).withQuery(query.take(caret - 1) + query.drop(caret), candidates)

  def deleteForward(candidates: Vector[DefNode]): TuiState =
    if (caret >= query.length) this
    else withQuery(query.take(caret) + query.drop(caret + 1), candidates)

  def moveCaret(step: Int): TuiState =
    copy(caret = math.max(0, math.min(caret + step, query.length)))

  def moveCursor(step: Int): TuiState = screen match {
    case TuiScreen.Options =>
      val options = SliceOptionLabel.values.length
      copy(optionCursor = SliceOptionLabel.fromOrdinal(math.floorMod(optionCursor.ordinal + step, options)))
    case TuiScreen.Confirm => this
    case TuiScreen.Done    => this
    case TuiScreen.Search =>
      val size = matches.size
      copy(cursor = if (size <= 0) 0 else math.floorMod(cursor + step, size))
  }

  def toggleOption: TuiState = {
    val next =
      if (enabledOptions.contains(optionCursor)) enabledOptions - optionCursor
      else enabledOptions + optionCursor
    copy(enabledOptions = next)
  }

  def showOptions: TuiState = copy(screen = TuiScreen.Options)

  def hideOptions: TuiState = copy(screen = TuiScreen.Search)

  def askToConfirm: TuiState = copy(screen = TuiScreen.Confirm, answer = ConfirmationAnswer.Yes)

  def moveChoice(step: Int): TuiState =
    copy(answer = ConfirmationAnswer.answerAtOrdinal(answer.ordinal + step))

  def backToSearch: TuiState = copy(screen = TuiScreen.Search, outcome = None)

  def showOutcome(finished: Either[String, Path]): TuiState =
    copy(screen = TuiScreen.Done, outcome = Some(finished))
}

private[tui] object TuiState {

  given Eq[TuiState] = Eq.fromUniversalEquals

  def initial(
      candidates: Vector[DefNode],
      terminal: TerminalSize,
      query: String,
      options: SliceOptions
  ): TuiState = {
    val matches = FuzzySearch.rankCandidates(query, candidates)
    TuiState(
      query = query,
      caret = query.length,
      matches = matches,
      cursor = 0,
      screen = if (matches.size === 1) TuiScreen.Confirm else TuiScreen.Search,
      optionCursor = SliceOptionLabel.FollowImplementations,
      answer = ConfirmationAnswer.Yes,
      enabledOptions = SliceOptionLabel.values.filter(_.isEnabled(options)).toSet,
      running = false,
      outcome = None,
      terminal = terminal
    )
  }
}
