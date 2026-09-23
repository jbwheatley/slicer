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

import scala.util.Try

import slicer.model.{SliceArguments, SliceFailure}
import slicer.util.ConsolePrint.*

import tui.viewport.TerminalSizeTracking

private[slicer] object ForkedSlicePicker extends SlicePicker(TerminalSizeTracking(), tickIntervalMs = 8L) {

  def main(args: Array[String]): Unit =
    SliceArguments.toFields(args.toVector).flatMap(openRequestedPicker) match {
      case Left(error) =>
        System.err.println(s"$error".toConsoleMessage)
        sys.exit(1)
      case Right(_) => ()
    }

  override protected def openPickerImpl(tui: SliceTui): Either[SliceFailure, Unit] =
    if (!runsOnATerminal())
      Left(
        SliceFailure(
          "slicer opens an interactive tui and this run has no terminal to open it on - run it from a shell."
        )
      )
    else
      SliceTui.runOnSttyTerminal(tui)

  private def runsOnATerminal(): Boolean = {
    val test = ProcessBuilder("sh", "-c", "test -t 0 && test -t 1").inheritIO()
    Try(test.start().waitFor()).toOption.contains(0)
  }
}
