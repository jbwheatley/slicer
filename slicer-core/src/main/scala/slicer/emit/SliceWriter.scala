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

package slicer.emit

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slicer.analysis.Index
import slicer.model.{BuildTool, SliceResult}

private[slicer] object SliceWriter {

  def sliceFiles(index: Index, result: SliceResult, sourceRoot: Path): Vector[(Path, String)] =
    sliceFilesWithStages(index = index, result = result, sourceRoot = sourceRoot)._1

  def sliceFilesWithStages(
      index: Index,
      result: SliceResult,
      sourceRoot: Path
  ): (Vector[(Path, String)], Set[Path]) = {
    val emitted = index.defsByFile.keys.toVector.sortBy(_.toString).flatMap { file =>
      Emit
        .sliceFileText(index, file, result.kept, result.implementationChoices)
        .map(sliced => (file, Emit.toRelativeSlicePath(sourceRoot, file), sliced))
    }

    val compiledFirst = MacroSources.collectFilesCompiledFirst(
      index = index,
      kept = result.kept,
      sliced = emitted.map { case (file, relative, _) => file -> relative }
    )

    (emitted.map { case (_, relative, sliced) => relative -> sliced }, compiledFirst)
  }

  def writeSlice(index: Index, result: SliceResult, sourceRoot: Path, out: Path, tool: BuildTool): Vector[Path] = {
    val (emitted, compiledFirst) = sliceFilesWithStages(index = index, result = result, sourceRoot = sourceRoot)
    val written = emitted.map { case (relative, sliced) =>
      val target = out.resolve(relative)
      Files.createDirectories(target.getParent)
      Files.write(target, sliced.getBytes(StandardCharsets.UTF_8))
      target
    }
    Files.createDirectories(out)
    Emit.writeBuildFiles(out, written, compiledFirst.map(out.resolve), tool)
    written
  }
}
