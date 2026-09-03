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

import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.atomic.AtomicBoolean

import scala.util.Try

import layoutz.Terminal

final class NonScrollingTerminal(inner: Terminal) extends Terminal {

  private val pending: StringBuilder = StringBuilder()

  override def enterRawMode(): Unit = {
    inner.enterRawMode()
    NonScrollingTerminal.freeControlKeys()
    inner.write(TerminalEscapes.enterAlternateScreen)
    inner.flush()
  }

  override def exitRawMode(): Unit = {
    inner.write(TerminalEscapes.leaveAlternateScreen)
    inner.flush()
    inner.exitRawMode()
  }

  override def clearScreen(): Unit = inner.clearScreen()

  override def clearScrollback(): Unit = ()

  override def hideCursor(): Unit = inner.hideCursor()

  override def showCursor(): Unit = inner.showCursor()

  override def write(value: String): Unit = pending.append(replaceLineFeeds(value))

  override def writeLine(value: String): Unit = write(s"$value\n")

  override def flush(): Unit = {
    val frame = pending.toString
    pending.setLength(0)
    if (frame.nonEmpty) inner.write(NonScrollingTerminal.frameWithReset(frame))
    inner.flush()
  }

  override def readInput(): Int = inner.readInput()

  override def readInputNonBlocking(): Option[Int] = inner.readInputNonBlocking()

  override def close(): Unit = inner.close()

  override def terminalWidth(): Int = inner.terminalWidth()

  private def replaceLineFeeds(value: String): String =
    value.replace("\n", TerminalEscapes.startOfNextRow)
}

object NonScrollingTerminal {

  private val wipeWanted: AtomicBoolean = AtomicBoolean(false)

  def wipeBeforeNextFrame(): Unit = wipeWanted.set(true)

  private def frameWithReset(frame: String): String = {
    val wipe = if (wipeWanted.getAndSet(false)) TerminalEscapes.wholeDisplay + TerminalEscapes.topLeftCorner else ""
    wipe + frame + TerminalEscapes.restOfDisplay + TerminalEscapes.topLeftCorner
  }

  private def freeControlKeys(): Unit = {
    val stty = ProcessBuilder("sh", "-c", "stty -iexten discard undef < /dev/tty 2>/dev/null")
      .redirectOutput(Redirect.DISCARD)
      .redirectError(Redirect.DISCARD)
    Try {
      val running = stty.start()
      running.getOutputStream.close()
      running.waitFor()
    }: Unit
  }
}
