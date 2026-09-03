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

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import cats.syntax.eq.*
import sbt.internal.util.Terminal
import tui.viewport.TerminalEscapes

private[sbt] final class SbtTerminal(terminal: Terminal, pump: SbtInputPump) extends layoutz.Terminal {

  private val typed: BlockingQueue[Integer] = LinkedBlockingQueue[Integer]()

  private val escapeGraceMs: Long = 6

  private val endOfInput: Integer = -1

  private val endOfInputRearmMs: Long = 5

  private val quit: Int = 17

  override def enterRawMode(): Unit = {
    terminal.setEchoEnabled(false)
    terminal.enterRawMode()
    pump.forwardInputTo(terminal, typed)
  }

  override def exitRawMode(): Unit = {
    pump.stopForwarding()
    typed.put(endOfInput)
    terminal.exitRawMode()
    terminal.setEchoEnabled(true)
  }

  override def clearScreen(): Unit = write(TerminalEscapes.wholeDisplay + TerminalEscapes.topLeftCorner)

  override def clearScrollback(): Unit = ()

  def clearBelowCursor(): Unit = write(TerminalEscapes.startOfLine + TerminalEscapes.restOfDisplay)

  override def hideCursor(): Unit = write(TerminalEscapes.cursorOff)

  override def showCursor(): Unit = write(TerminalEscapes.cursorOn)

  override def write(value: String): Unit = terminal.printStream.print(value)

  override def writeLine(value: String): Unit = write(s"$value\n")

  override def flush(): Unit = terminal.printStream.flush()

  override def readInput(): Int = endInputAfterQuit(keepEndOfInput(typed.take().intValue))

  private def endInputAfterQuit(byte: Int): Int = {
    if (byte === quit) typed.put(endOfInput)
    byte
  }

  override def readInputNonBlocking(): Option[Int] =
    Option(typed.poll(escapeGraceMs, TimeUnit.MILLISECONDS))
      .map(byte => keepEndOfInput(byte.intValue))
      .filter(_ >= 0)

  private def keepEndOfInput(byte: Int): Int = {
    if (byte < 0) {
      Thread.sleep(endOfInputRearmMs)
      typed.put(endOfInput)
    }
    byte
  }

  override def close(): Unit = {
    exitRawMode()
    flush()
  }

  override def terminalWidth(): Int = terminal.getWidth
}
