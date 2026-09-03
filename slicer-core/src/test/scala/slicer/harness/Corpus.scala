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

package slicer.harness

import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import slicer.analysis.{Index, Reachability, ScalaVersionRules}
import slicer.emit.{ScalacOptions, SliceWriter}
import slicer.model.*

import cats.syntax.eq.*

class Corpus(projectPath: String, modules: Vector[String], layout: CorpusLayout, val buildTool: BuildTool) {

  private def ancestorsOf(dir: Path): LazyList[Path] =
    dir #:: (Option(dir.getParent) match {
      case Some(parent) => ancestorsOf(parent)
      case None         => LazyList.empty
    })

  val repoRoot: Path = {
    val start = Paths.get("").toAbsolutePath.normalize()
    ancestorsOf(start)
      .find(dir => Files.isDirectory(dir.resolve(projectPath)) && Files.isRegularFile(dir.resolve("build.sbt")))
      .getOrElse(sys.error("could not find the repository root above " + start))
  }

  val projectRoot: Path = repoRoot.resolve(projectPath)

  val semanticdbDirs: Vector[Path] = {
    val out = projectRoot.resolve(layout.outputPath)
    if (!Files.isDirectory(out)) Vector.empty
    else
      Using.resource(Files.walk(out, Corpus.searchDepth))(
        _.iterator().asScala
          .filter(p =>
            p.getFileName.toString === layout.semanticdbDirName && Files.isDirectory(p.resolve("META-INF/semanticdb"))
          )
          .toVector
          .sortBy(_.toString)
      )
  }

  val sourceDirs: Vector[Path] =
    modules.map(m => projectRoot.resolve(s"$m/src/main/scala")) ++
      modules.map(m => projectRoot.resolve(s"$m/src/main/java"))

  val scalaVersion: String = buildTool.scalaVersion

  val platform: Platform = buildTool.platform

  val dependencies: Vector[Dependency] = buildTool.dependencies

  val classpathDependencies: Vector[Dependency] =
    Dependency.filterToScope(dependencies, DependencyScope.Compile) ++
      Dependency.filterToScope(dependencies, DependencyScope.Provided)

  val pluginDependencies: Vector[Dependency] = Dependency.filterToScope(dependencies, DependencyScope.Plugin)

  val scalacOptions: Vector[String] = ScalacOptions.filterForSlice(buildTool.scalacOptions)

  lazy val language: ScalaVersionRules = ScalaVersionRules.rulesForScalaVersion(scalaVersion)

  private val missingCorpus =
    s"""|the test project has no SemanticDB output under ${projectRoot.resolve(layout.outputPath)}.
        |Build the corpus first:  (cd $projectPath && ${layout.buildCommand})""".stripMargin

  lazy val index: Index = {
    if (semanticdbDirs.isEmpty) sys.error(missingCorpus)
    val built = Index.build(projectRoot, semanticdbDirs, sourceDirs, language)
    if (built.defs.isEmpty) sys.error(missingCorpus)
    built
  }

  lazy val allSymbols: Vector[DefNode] = index.defs.values.toVector.sortBy(_.symbol)

  def resolve(query: String): DefNode =
    index.resolveQuery(query) match {
      case Seq(one) => one
      case Seq()    => sys.error(s"no definition matches '$query'")
      case many     => sys.error(s"'$query' is ambiguous: ${many.map(_.dottedName).mkString(", ")}")
    }

  def symbolOf(query: String): Symbol = resolve(query).symbol

  def slice(query: String): Slice =
    sliceOf(resolve(query), SliceOptions(followImplementations = true, keepFields = false))

  def slice(query: String, options: SliceOptions): Slice =
    sliceOf(resolve(query), options)

  def sliceOf(root: DefNode, options: SliceOptions): Slice = {
    val result = Reachability.computeSliceResult(index, root, options)
    val (emitted, compiledFirst) = SliceWriter.sliceFilesWithStages(index, result, projectRoot)
    val files = emitted.map { case (relative, body) => relative.toString -> body }.toMap
    Slice(root, result, files, compiledFirst.map(_.toString))
  }
}

object Corpus {

  val searchDepth: Int = 6

  val dependencies: Vector[Dependency] =
    Vector(
      Dependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, false),
      Dependency("com.lihaoyi", "sourcecode", "0.4.2", CrossVersion.Binary, DependencyScope.Provided, false)
    )

  val scala2Dependencies: Vector[Dependency] =
    dependencies ++ Vector(
      Dependency("org.scala-lang", "scala-reflect", "2.13.16", CrossVersion.Disabled, DependencyScope.Compile, false),
      Dependency("org.typelevel", "kind-projector", "0.13.3", CrossVersion.Full, DependencyScope.Plugin, false)
    )

  val platformDependencies: Vector[Dependency] =
    Vector(Dependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, true))

  val scalacOptions: Vector[String] = Vector("-Xkind-projector")

  val scala2ScalacOptions: Vector[String] = Vector.empty
}
