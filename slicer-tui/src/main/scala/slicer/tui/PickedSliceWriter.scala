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

import slicer.analysis.{Index, Reachability}
import slicer.emit.SliceWriter
import slicer.model.{BuildTool, DefNode, SliceOptions}

private[tui] object PickedSliceWriter {

  def writeSlice(
      index: Index,
      root: DefNode,
      options: SliceOptions,
      sourceRoot: Path,
      out: Path,
      tool: BuildTool
  ): Path = {
    val target = out.resolve(root.symbol.toDirectoryName)
    val result = Reachability.computeSliceResult(index, root, options)
    SliceWriter.writeSlice(index = index, result = result, sourceRoot = sourceRoot, out = target, tool = tool): Unit
    target
  }
}
