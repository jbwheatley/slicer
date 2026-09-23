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

import slicer.model.{SliceArguments, SliceFailure, SliceOptions}

import tui.viewport.TerminalSizeTracking

trait SlicePicker(sizing: TerminalSizeTracking, tickIntervalMs: Long) {
  private def tui(inputs: SliceInputs, query: String, options: SliceOptions) =
    SliceTui.build(inputs, sizes = sizing, tickIntervalMs = tickIntervalMs, query = query, options = options)

  final def openPicker(inputs: SliceInputs, query: String, options: SliceOptions): Either[SliceFailure, Unit] =
    openPickerImpl(tui(inputs = inputs, query = query, options = options))

  final def openRequestedPicker(fields: SliceArguments.Fields): Either[SliceFailure, Unit] =
    for {
      sourceRoot <- SliceArguments.readSourceRoot(fields)
      out <- SliceArguments.readOut(fields)
      semanticdbDirs <- SliceArguments.readSemanticdbDirs(fields)
      sourceDirs <- SliceArguments.readSourceDirs(fields)
      tool <- SliceArguments.readBuildTool(fields)
      options <- SliceArguments.readOptions(fields)
      inputs <- SliceInputs.build(
        sourceRoot = sourceRoot,
        semanticdbDirs = semanticdbDirs,
        sourceDirs = sourceDirs,
        out = out,
        tool = tool
      )
      outcome <- openPicker(inputs = inputs, query = SliceArguments.readQuery(fields), options = options)
    } yield outcome

  protected def openPickerImpl(tui: SliceTui): Either[SliceFailure, Unit]
}
