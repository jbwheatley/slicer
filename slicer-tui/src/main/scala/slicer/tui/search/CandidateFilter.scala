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

package slicer.tui.search

import slicer.analysis.Index
import slicer.model.{DefKind, DefNode, SliceOptions}

import cats.syntax.eq.*

private[tui] object CandidateFilter {

  private val skippedKinds: Set[DefKind] =
    Set(DefKind.Param, DefKind.EnumCase, DefKind.Type, DefKind.Enum, DefKind.Extension, DefKind.JavaType)

  def selectSliceCandidates(index: Index, options: SliceOptions): Vector[DefNode] =
    index.defs.values.toVector.filter(node => isOfferedAsSliceRoot(index = index, node = node, options = options))

  private def isOfferedAsSliceRoot(index: Index, node: DefNode, options: SliceOptions): Boolean =
    if (skippedKinds.contains(node.kind) || node.symbol.isSynthetic) false
    else if (node.kind === DefKind.Trait)
      if (options.followImplementations) hasMethods(index, node) && hasImplementations(index, node)
      else hasConcreteMethods(index, node)
    else if (node.isContainer) hasMethods(index, node)
    else true

  private def hasConcreteMethods(index: Index, node: DefNode): Boolean =
    collectMethods(index, node).exists(!_.isAbstract)

  private def collectMethods(index: Index, node: DefNode): Set[DefNode] =
    index.membersByOwner
      .getOrElse(node.symbol, Set.empty)
      .filter(m => m.kind === DefKind.Def || m.kind === DefKind.Given)

  private def hasMethods(index: Index, node: DefNode): Boolean = collectMethods(index, node).nonEmpty

  private def hasImplementations(index: Index, node: DefNode): Boolean =
    collectMethods(index, node).exists(m => index.overriddenBy.getOrElse(m.symbol, Set.empty).nonEmpty)
}
