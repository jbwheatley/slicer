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

import java.nio.file.Path

import scala.annotation.tailrec

import slicer.analysis.Index
import slicer.model.Symbol

import cats.syntax.eq.*

private[emit] object MacroSources {

  def collectFilesCompiledFirst(index: Index, kept: Set[Symbol], sliced: Vector[(Path, Path)]): Set[Path] = {
    val implementations = index.macroImplementations.filter(kept)
    val seeds = implementations.flatMap(symbol => index.defs.get(symbol)).map(_.file)
    if (seeds.isEmpty) Set.empty
    else {
      val closure = growFileClosure(index = index, kept = kept, files = seeds)
      val roots = sliced.collect {
        case (file, relative) if closure.contains(file) => SourceLayout.findSourceRoot(relative)
      }
      sliced.collect { case (_, relative) if roots.contains(SourceLayout.findSourceRoot(relative)) => relative }.toSet
    }
  }

  @tailrec
  private def growFileClosure(index: Index, kept: Set[Symbol], files: Set[Path]): Set[Path] = {
    val reached = index.defs.values.toVector
      .filter(node => kept(node.symbol) && files.contains(node.file))
      .flatMap(node => index.edges.getOrElse(node.symbol, Set.empty))
      .filter(kept)
      .flatMap(index.defs.get)
      .map(_.file)
      .toSet

    val grown = files ++ reached
    if (grown === files) files else growFileClosure(index = index, kept = kept, files = grown)
  }
}
