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

private[viewport] final class ThrottledTerminalSizeTracking extends TerminalSizeTracking {

  private final case class MeasuredSize(millis: Long, size: TerminalSize)

  private val remeasureIntervalMs: Long = 250L

  private def measureSize(): MeasuredSize = MeasuredSize(System.currentTimeMillis(), TerminalSize.detectTerminalSize())

  private val latest: AtomicReference[MeasuredSize] = AtomicReference(measureSize())

  override def currentSize(): TerminalSize = {
    val previous = latest.get()
    if (System.currentTimeMillis() - previous.millis < remeasureIntervalMs) previous.size
    else {
      val measured = measureSize()
      latest.set(measured)
      measured.size
    }
  }
}
