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

package slicer.mill

import scala.util.Try

import slicer.model.SliceFailure
import slicer.tui.{SlicePicker, SliceTui}
import slicer.util.ConsolePrint.*

import tui.viewport.TerminalSizeTracking

private[slicer] object MillSlicePicker extends SlicePicker(TerminalSizeTracking(), tickIntervalMs = 8L) {

  val mainClass: String = "slicer.mill.MillSlicePicker"

  def main(args: Array[String]): Unit = {
    val picked = for {
      fields <- SliceArguments.toFields(args.toVector)
      sourceRoot <- SliceArguments.readSourceRoot(fields)
      out <- SliceArguments.readOut(fields)
      semanticdbDirs <- SliceArguments.readSemanticdbDirs(fields)
      sourceDirs <- SliceArguments.readSourceDirs(fields)
      tool <- SliceArguments.readBuildTool(fields)
      options <- SliceArguments.readOptions(fields)
      inputs <- MillSliceInputs.buildSliceInputs(
        sourceRoot = sourceRoot,
        semanticdbDirs = semanticdbDirs,
        sourceDirs = sourceDirs,
        out = out,
        tool = tool
      )
      outcome <- openPicker(inputs = inputs, query = SliceArguments.readQuery(fields), options = options)
    } yield outcome

    picked match {
      case Left(error) =>
        System.err.println(s"$error".toConsoleMessage)
        sys.exit(1)
      case Right(_) => ()
    }
  }

  override protected def openPickerImpl(tui: SliceTui): Either[SliceFailure, Unit] =
    if (!runsOnATerminal())
      Left(
        SliceFailure(
          "slicer opens an interactive tui and this run has no terminal to open it on - run './mill slice' from a shell."
        )
      )
    else
      SliceTui.runOnSttyTerminal(tui)

  private def runsOnATerminal(): Boolean = {
    val test = ProcessBuilder("sh", "-c", "test -t 0 && test -t 1").inheritIO()
    Try(test.start().waitFor()).toOption.contains(0)
  }
}
