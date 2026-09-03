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

import java.util.concurrent.atomic.AtomicReference

import layoutz.Terminal

class NonScrollingTerminalSuite extends munit.FunSuite {

  private final class RecordingTerminal extends Terminal {
    private val recorded: AtomicReference[Vector[String]] = AtomicReference(Vector.empty)

    def writes: Vector[String] = recorded.get()

    override def enterRawMode(): Unit = ()
    override def exitRawMode(): Unit = ()
    override def clearScreen(): Unit = ()
    override def clearScrollback(): Unit = ()
    override def hideCursor(): Unit = ()
    override def showCursor(): Unit = ()
    override def write(value: String): Unit = {
      recorded.updateAndGet(seen => seen :+ value)
      ()
    }

    override def writeLine(value: String): Unit = write(s"$value\n")
    override def flush(): Unit = ()
    override def readInput(): Int = -1
    override def readInputNonBlocking(): Option[Int] = None
    override def close(): Unit = ()
    override def terminalWidth(): Int = 80
  }

  private val escape = "\u001b"

  test("the app lives on the alternate screen, so a resize cannot scroll it") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    terminal.enterRawMode()
    assertEquals(inner.writes, Vector(TerminalEscapes.enterAlternateScreen))
    terminal.exitRawMode()
    assertEquals(inner.writes.last, TerminalEscapes.leaveAlternateScreen)
  }

  test("a frame reaches the terminal as a single write, with no line feeds to scroll on") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    terminal.write("first\n")
    terminal.write("second\n")
    terminal.flush()
    assertEquals(inner.writes.size, 1)
    assert(!inner.writes.head.contains("\n"), inner.writes.head)
    assert(inner.writes.head.contains("first"), inner.writes.head)
    assert(inner.writes.head.contains("second"), inner.writes.head)
  }

  test("every frame sweeps away whatever sat below it") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    terminal.write("only")
    terminal.flush()
    assert(inner.writes.head.contains(s"$escape[0J"), inner.writes.head)
  }

  test("the cursor parks at the top, so shrinking the window clips the bottom instead of scrolling") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    terminal.write("only")
    terminal.flush()
    assert(inner.writes.head.endsWith(s"$escape[H"), inner.writes.head)
  }

  test("a wanted wipe rides along with the next frame rather than blanking the screen alone") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    NonScrollingTerminal.wipeBeforeNextFrame()
    terminal.write("fresh")
    terminal.flush()
    assertEquals(inner.writes.size, 1)
    assert(inner.writes.head.startsWith(s"$escape[2J$escape[H"), inner.writes.head)
    assert(inner.writes.head.contains("fresh"), inner.writes.head)
  }

  test("a wipe is spent once and does not haunt later frames") {
    val inner = RecordingTerminal()
    val terminal = NonScrollingTerminal(inner)
    NonScrollingTerminal.wipeBeforeNextFrame()
    terminal.write("first")
    terminal.flush()
    terminal.write("second")
    terminal.flush()
    assert(!inner.writes.last.contains(s"$escape[2J"), inner.writes.last)
  }
}
