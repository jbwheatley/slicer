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

package slicer.analysis

import java.nio.file.Path

import scala.meta.*
import scala.meta.internal.semanticdb

import slicer.model.Symbol

import cats.syntax.eq.*

private[slicer] sealed trait ScalaVersionRules {

  def name: String

  def dialects: Vector[Dialect]

  def semanticdbOptions: Vector[String]

  def collectConversions(tree: Tree, symbolAtStart: Map[Int, Symbol]): Vector[Symbol]

  def checkForMissingSynthetics(docs: Vector[semanticdb.TextDocument]): Option[String]
}

private[slicer] object ScalaVersionRules {

  private lazy val versionInPath = """scala-([23]\.\d+\.\d+(?:-[\w.]+)?|3)(?:[/\\]|$)""".r

  def rulesForScalaVersion(version: String): ScalaVersionRules =
    if (version.startsWith("2.13")) Scala213Rules else Scala3Rules

  def rulesForSemanticdbDirs(dirs: Vector[Path]): ScalaVersionRules =
    dirs.iterator
      .flatMap(dir => versionInPath.findFirstMatchIn(dir.toString).map(_.group(1)))
      .nextOption()
      .map(rulesForScalaVersion)
      .getOrElse(Scala3Rules)

  case object Scala213Rules extends ScalaVersionRules {

    override val name: String = "Scala 2.13"

    override val dialects: Vector[Dialect] =
      Vector(scala.meta.dialects.Scala213, scala.meta.dialects.Scala213Source3)

    override val semanticdbOptions: Vector[String] = Vector("-P:semanticdb:synthetics:on")

    override def collectConversions(tree: Tree, symbolAtStart: Map[Int, Symbol]): Vector[Symbol] =
      collectImplicitConversions(tree, symbolAtStart)

    override def checkForMissingSynthetics(docs: Vector[semanticdb.TextDocument]): Option[String] =
      if (docs.nonEmpty && docs.forall(_.synthetics.isEmpty))
        Some(
          "SemanticDB carries no synthetics, so implicit arguments, conversions and for-comprehensions " +
            "are invisible to the slicer. Compile with -P:semanticdb:synthetics:on."
        )
      else None
  }

  case object Scala3Rules extends ScalaVersionRules {

    override val name: String = "Scala 3"

    override val dialects: Vector[Dialect] = Vector(scala.meta.dialects.Scala3)

    override val semanticdbOptions: Vector[String] = Vector.empty

    private def isConversion(tpe: Type): Boolean = tpe match {
      case Type.Apply.After_4_6_0(Type.Name("Conversion"), _)                 => true
      case Type.Apply.After_4_6_0(Type.Select(_, Type.Name("Conversion")), _) => true
      case _                                                                  => false
    }

    override def collectConversions(tree: Tree, symbolAtStart: Map[Int, Symbol]): Vector[Symbol] = {
      val givens = tree
        .collect {
          case d: Defn.GivenAlias if isConversion(d.decltpe)                   => symbolAtStart.get(d.pos.start)
          case d: Defn.Given if d.templ.inits.exists(i => isConversion(i.tpe)) => symbolAtStart.get(d.pos.start)
        }
        .flatten
        .toVector

      givens ++ collectImplicitConversions(tree, symbolAtStart)
    }

    override def checkForMissingSynthetics(docs: Vector[semanticdb.TextDocument]): Option[String] = None
  }

  private def collectImplicitConversions(tree: Tree, symbolAtStart: Map[Int, Symbol]): Vector[Symbol] =
    tree
      .collect {
        case d: Defn.Def if isImplicit(d.mods) && convertsOneArgument(d.paramClauses) =>
          symbolAtStart.get(d.pos.start)
        case d: Defn.Class if isImplicit(d.mods) =>
          symbolAtStart.get(d.pos.start)
      }
      .flatten
      .toVector

  private def isImplicit(mods: List[Mod]): Boolean = mods.exists(_.is[Mod.Implicit])

  private def convertsOneArgument(clauses: Seq[Term.ParamClause]): Boolean =
    clauses.headOption.exists(clause =>
      clause.values.size === 1 && !clause.values.exists(_.mods.exists(_.is[Mod.Implicit]))
    )
}
