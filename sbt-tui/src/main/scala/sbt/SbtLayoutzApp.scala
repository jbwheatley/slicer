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

package sbt

import scala.util.Using
import scala.util.Using.Releasable

import layoutz.{Alignment, Key, LayoutzApp}
import sbt.internal.util.Terminal
import tui.viewport.{NonScrollingTerminal, TerminalEscapes}

final class SbtLayoutzApp[State, Message] private (app: LayoutzApp[State, Message], terminal: Terminal) {

  private given Releasable[SbtTerminal] = screen => {
    screen.write(TerminalEscapes.leaveAlternateScreen)
    screen.showCursor()
    screen.clearBelowCursor()
    screen.close()
  }

  // scalafix:off DisableSyntax.defaultArgs
  def run(
      tickIntervalMs: Long = 250L,
      renderIntervalMs: Long = 16L,
      quitKey: Key = Key.Ctrl('Q'),
      showQuitMessage: Boolean = false,
      quitMessage: String = "Press Ctrl+Q to quit",
      alignment: Alignment = Alignment.Left
  ): Unit =
    Using.resource(SbtTerminal(terminal, SbtInputPump()))(screen =>
      terminal.withRawOutput(
        app.run(
          tickIntervalMs = tickIntervalMs,
          renderIntervalMs = renderIntervalMs,
          quitKey = quitKey,
          showQuitMessage = showQuitMessage,
          quitMessage = quitMessage,
          clearOnStart = false,
          alignment = alignment,
          terminal = Some(NonScrollingTerminal(screen))
        )
      )
    )
  // scalafix:on DisableSyntax.defaultArgs
}

object SbtLayoutzApp {

  def create[State, Message](app: LayoutzApp[State, Message]): SbtLayoutzApp[State, Message] =
    Option(Terminal.get)
      .filter(_.isAnsiSupported)
      .map(terminal => SbtLayoutzApp(app, terminal))
      .getOrElse(
        throw new RuntimeException("Needs an interactive terminal - run from an sbt shell that has one.")
      ) // scalafix:ok DisableSyntax.throw
}
