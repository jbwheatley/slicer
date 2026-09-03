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

import java.nio.file.Path

import slicer.model.*
import slicer.tui.SliceInputs

import mill.javalib.Dep

private[slicer] object MillSliceInputs {

  def toDependency(dep: Dep, scope: DependencyScope): Dependency =
    Dependency(
      organization = dep.organization,
      artifact = dep.name,
      version = dep.version,
      crossVersion =
        if (dep.cross.isFull) CrossVersion.Full
        else if (dep.cross.isBinary) CrossVersion.Binary
        else CrossVersion.Disabled,
      scope = scope,
      platformed = dep.cross.platformed
    )

  def collectDependencies(deps: Seq[Dep], scope: DependencyScope): Vector[Dependency] =
    Dependency.sortDependencies(deps.map(dep => toDependency(dep, scope)))

  def buildSliceInputs(
      sourceRoot: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      out: Path,
      tool: BuildTool.Mill
  ): Either[SliceFailure, SliceInputs] =
    SliceInputs.build(
      sourceRoot = sourceRoot,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      out = out,
      tool = tool,
      compileFirstAdvice = "run ./mill __.semanticDbData first"
    )
}
