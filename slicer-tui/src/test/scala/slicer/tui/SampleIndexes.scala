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

import java.nio.file.{Path, Paths}

import slicer.analysis.Index
import slicer.model.{DefKind, DefNode, Symbol}

import cats.syntax.eq.*

object SampleIndexes {

  val longFile: Path = Paths.get("/home/someone/project/src/main/scala/com/example/Long.scala")

  val longSource: String = 0.to(400).map(line => s"  val line$line = ${"x" * 200}").mkString("\n")

  val shortFile: Path = Paths.get("/home/someone/project/src/main/scala/com/example/Short.scala")

  val shortSource: String = "  val one = 1"

  val longDefinition: DefNode = DefNode(
    symbol = Symbol("com/example/Long#everything()."),
    kind = DefKind.Def,
    displayName = "everything",
    file = longFile,
    start = 0,
    end = longSource.length,
    owner = Some(Symbol("com/example/Long#")),
    isAbstract = false,
    expandsAtCallSite = false
  )

  val shortDefinition: DefNode = DefNode(
    symbol = Symbol("com/example/Short#one()."),
    kind = DefKind.Def,
    displayName = "one",
    file = shortFile,
    start = 0,
    end = shortSource.length,
    owner = Some(Symbol("com/example/Short#")),
    isAbstract = false,
    expandsAtCallSite = false
  )

  val longlyNamedDefinition: DefNode = DefNode(
    symbol = Symbol("com/example/deeply/nested/package/Holder#soughtAfterMember()."),
    kind = DefKind.Def,
    displayName = "soughtAfterMember",
    file = shortFile,
    start = 0,
    end = shortSource.length,
    owner = Some(Symbol("com/example/deeply/nested/package/Holder#")),
    isAbstract = false,
    expandsAtCallSite = false
  )

  val mixedFile: Path = Paths.get("/home/someone/project/src/main/Mixed.scala")

  val mixedDefinitions: Vector[DefNode] = 0
    .to(200)
    .map { position =>
      val name = if (position % 97 === 0) "muchLongerMemberName" * 3 + position else s"member$position"
      DefNode(
        symbol = Symbol(s"com/example/Mixed#$name()."),
        kind = DefKind.Def,
        displayName = name,
        file = mixedFile,
        start = 0,
        end = shortSource.length,
        owner = Some(Symbol("com/example/Mixed#")),
        isAbstract = false,
        expandsAtCallSite = false
      )
    }
    .toVector

  val empty: Index = indexOf(Map.empty, Map.empty)

  val withLongNames: Index = indexOf(
    Map(longlyNamedDefinition.symbol -> longlyNamedDefinition),
    Map(shortFile -> shortSource)
  )

  val withMixedNames: Index = indexOf(
    mixedDefinitions.map(node => node.symbol -> node).toMap,
    Map(mixedFile -> shortSource)
  )

  val withLongSource: Index = indexOf(
    Map(longDefinition.symbol -> longDefinition, shortDefinition.symbol -> shortDefinition),
    Map(longFile -> longSource, shortFile -> shortSource)
  )

  def containing(nodes: Vector[DefNode], overriddenBy: Map[Symbol, Set[Symbol]]): Index =
    indexOf(
      defs = nodes.map(node => node.symbol -> node).toMap,
      sources = Map.empty,
      overriddenBy = overriddenBy
    )

  private def indexOf(defs: Map[Symbol, DefNode], sources: Map[Path, String]): Index =
    indexOf(defs = defs, sources = sources, overriddenBy = Map.empty)

  private def indexOf(
      defs: Map[Symbol, DefNode],
      sources: Map[Path, String],
      overriddenBy: Map[Symbol, Set[Symbol]]
  ): Index = Index(
    defs = defs,
    defsByFile = Map.empty,
    edges = Map.empty,
    overriddenBy = overriddenBy,
    overrides = Map.empty,
    supertypes = Map.empty,
    instantiations = Map.empty,
    structuralUses = Map.empty,
    trees = Map.empty,
    derivations = Map.empty,
    factoryTargets = Map.empty,
    exports = Map.empty,
    flags = Map.empty,
    macroImplementations = Set.empty,
    reflectiveTargets = Set.empty,
    sources = sources,
    warnings = Vector.empty
  )
}
