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

package slicer.tui

import java.nio.file.Path
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import scala.language.implicitConversions
import scala.util.Try

import slicer.analysis.Index
import slicer.model.*
import slicer.tui.model.*
import slicer.tui.render.ScreenRenderer
import slicer.tui.search.CandidateFilter
import slicer.tui.terminal.*
import slicer.util.ConsolePrint.*

import cats.syntax.eq.*
import layoutz.*
import tui.viewport.{NonScrollingTerminal, TerminalEscapes, TerminalSize, TerminalSizeTracking}

private[slicer] final class SliceTui private[tui] (
    index: Index,
    sourceRoot: Path,
    out: Path,
    tool: BuildTool,
    terminal: TerminalSize,
    sizes: TerminalSizeTracking,
    val tickIntervalMs: Long,
    query: String,
    options: SliceOptions
) extends LayoutzApp[TuiState, TuiMessage] {

  import TuiMessage._

  private lazy val candidatesFollowingImplementations: Vector[DefNode] =
    CandidateFilter.selectSliceCandidates(index, SliceOptions(followImplementations = true, keepFields = false))

  private lazy val candidatesStoppingAtAbstract: Vector[DefNode] =
    CandidateFilter.selectSliceCandidates(index, SliceOptions(followImplementations = false, keepFields = false))

  private val screens: ScreenRenderer =
    ScreenRenderer(index = index, sourceRoot = sourceRoot, out = out, tool = tool)

  private val lastFrame: AtomicReference[Option[(TuiState, Element)]] = AtomicReference(None)

  private val slicedDirectories: AtomicReference[Vector[Path]] = AtomicReference(Vector.empty)

  private val failures: AtomicReference[Vector[String]] = AtomicReference(Vector.empty)

  private val reported: AtomicBoolean = AtomicBoolean(false)

  def runOnScreen(screen: Terminal, clearOnStart: Boolean): Unit =
    run(
      tickIntervalMs = tickIntervalMs,
      renderIntervalMs = SliceTui.repaintIntervalMs,
      showQuitMessage = true,
      quitMessage = SliceTui.quitMessage,
      clearOnStart = clearOnStart,
      terminal = Some(NonScrollingTerminal(screen))
    )

  def reportingOnShutdown(work: => Unit): Unit = {
    val hook = Thread(() => Try(reportSession()): Unit)
    Runtime.getRuntime.addShutdownHook(hook)
    try work
    finally {
      Try(Runtime.getRuntime.removeShutdownHook(hook)): Unit
      reportSession()
    }
  }

  private def reportSession(): Unit = {
    val written = slicedDirectories.get()
    val failed = failures.get()
    val anythingToSay = written.nonEmpty || failed.nonEmpty || index.warnings.nonEmpty
    if (anythingToSay && reported.compareAndSet(false, true)) {
      print(TerminalEscapes.leaveAlternateScreen)
      index.warnings.foreach(warning => System.err.println(s"warning: $warning".toConsoleMessage))
      failed.foreach(error => System.err.println(s"failed: $error".toConsoleMessage))
      written.foreach(reportSlice)
    }
  }

  private def reportSlice(target: Path): Unit = {
    println(s"wrote $target".toConsoleMessage)
    OpenCommand.openCommandsForDirectory(target) match {
      case Vector() => ()
      case commands =>
        println("open it with one of:".toConsoleMessage)
        commands.foreach(open => println(s"  ${open.editor}: ${open.command}".toConsoleMessage))
    }
  }

  override def init: (TuiState, Cmd[TuiMessage]) = {
    val candidates =
      if (options.followImplementations) candidatesFollowingImplementations else candidatesStoppingAtAbstract
    (TuiState.initial(candidates, terminal, query, options), Cmd.none)
  }

  override def update(msg: TuiMessage, state: TuiState): (TuiState, Cmd[TuiMessage]) = msg match {
    case TypeCharacter(character) => (state.typeCharacter(character, chooseCandidates(state)), Cmd.none)
    case DeleteBackward           => (state.deleteBackward(chooseCandidates(state)), Cmd.none)
    case DeleteForward            => (state.deleteForward(chooseCandidates(state)), Cmd.none)
    case MoveCaret(step)          => (state.moveCaret(step), Cmd.none)
    case MoveUp                   => (state.moveCursor(-1), Cmd.none)
    case MoveDown                 => (state.moveCursor(1), Cmd.none)
    case ShowOptions              => (state.showOptions, Cmd.none)
    case HideOptions              => (state.hideOptions, Cmd.none)
    case ToggleOption =>
      val toggled = state.toggleOption
      (toggled.withQuery(toggled.query, chooseCandidates(toggled)), Cmd.none)
    case AskToConfirm =>
      state.selected match {
        case None    => (state, Cmd.none)
        case Some(_) => (state.askToConfirm, Cmd.none)
      }
    case MoveChoice(step) => (state.moveChoice(step), Cmd.none)
    case AnswerConfirmation =>
      state.answer match {
        case ConfirmationAnswer.Yes => update(RunSlice, state)
        case ConfirmationAnswer.No  => update(CancelSlice, state)
      }
    case CancelSlice               => (state.backToSearch, Cmd.none)
    case RunSlice if state.running => (state.backToSearch, Cmd.none)
    case RunSlice =>
      state.selected match {
        case None => (state.backToSearch, Cmd.none)
        case Some(root) =>
          val options = state.sliceOptions
          (
            state.backToSearch.copy(running = true),
            Cmd.task(writePickedSlice(root, options))(outcome => SliceFinished(outcome))
          )
      }
    case DismissOutcome => (state.backToSearch, Cmd.none)
    case SliceFinished(Right(target)) =>
      slicedDirectories.updateAndGet(written => if (written.contains(target)) written else written :+ target)
      (state.copy(running = false).showOutcome(Right(target)), Cmd.none)
    case SliceFinished(Left(error)) =>
      failures.updateAndGet(_ :+ error)
      (state.copy(running = false).showOutcome(Left(error)), Cmd.none)
    case SetTerminalSize(size) =>
      if (size === state.terminal) (state, Cmd.none)
      else (state.copy(terminal = size), Cmd.fire(NonScrollingTerminal.wipeBeforeNextFrame()))
  }

  override def subscriptions(state: TuiState): Sub[TuiMessage] = Sub.batch(
    Sub.time.everyDynamicMs(tickIntervalMs, () => SetTerminalSize(sizes.currentSize())),
    keySubscription(state)
  )

  private def keySubscription(state: TuiState): Sub[TuiMessage] =
    Sub.onKeyPress(key => TuiKeys.messageForKey(key, state.screen))

  override def view(state: TuiState): Element = lastFrame.get() match {
    case Some((drawnFor, frame)) if drawnFor === state => frame
    case _ =>
      val frame = Text(screens.renderScreen(state).render)
      lastFrame.set(Some((state, frame)))
      frame
  }

  private def chooseCandidates(state: TuiState): Vector[DefNode] =
    if (state.sliceOptions.followImplementations) candidatesFollowingImplementations
    else candidatesStoppingAtAbstract

  private def writePickedSlice(root: DefNode, options: SliceOptions): Path =
    PickedSliceWriter.writeSlice(
      index = index,
      root = root,
      options = options,
      sourceRoot = sourceRoot,
      out = out,
      tool = tool
    )
}

private[slicer] object SliceTui {

  val repaintIntervalMs: Long = 16

  val quitMessage: String = "[Ctrl+Q] to quit"

  def build(
      inputs: SliceInputs,
      sizes: TerminalSizeTracking,
      tickIntervalMs: Long,
      query: String,
      options: SliceOptions
  ): SliceTui =
    SliceTui(
      index = inputs.index,
      sourceRoot = inputs.sourceRoot,
      out = inputs.out,
      tool = inputs.tool,
      terminal = sizes.currentSize(),
      sizes = sizes,
      tickIntervalMs = tickIntervalMs,
      query = query,
      options = options
    )

  def runOnSttyTerminal(tui: SliceTui): Either[SliceFailure, Unit] =
    SttyTerminal.create() match {
      case Left(error) =>
        error match {
          case TerminalError(message, cause) => Left(SliceFailure(message, cause))
          case RenderError(message, cause)   => Left(SliceFailure(message, cause))
          case InputError(message, cause)    => Left(SliceFailure(message, cause))
        }
      case Right(screen) => Right(tui.reportingOnShutdown(tui.runOnScreen(screen, clearOnStart = true)))
    }

}
