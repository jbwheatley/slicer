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

import slicer.analysis.{Index, ScalaVersionRules}
import slicer.model.{BuildTool, SliceFailure}

private[slicer] final case class SliceInputs(index: Index, sourceRoot: Path, out: Path, tool: BuildTool)

private[slicer] object SliceInputs {

  def build(
      sourceRoot: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      out: Path,
      tool: BuildTool,
      compileFirstAdvice: String
  ): Either[SliceFailure, SliceInputs] =
    if (semanticdbDirs.isEmpty) Left(SliceFailure(s"slice found no SemanticDB output; $compileFirstAdvice"))
    else {
      val index =
        Index.build(sourceRoot, semanticdbDirs, sourceDirs, ScalaVersionRules.rulesForScalaVersion(tool.scalaVersion))

      if (index.defs.isEmpty) Left(SliceFailure(s"slice found no definitions under $sourceRoot; $compileFirstAdvice"))
      else Right(SliceInputs(index = index, sourceRoot = sourceRoot, out = out, tool = tool))
    }
}
