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

package slicer.corpus

import java.nio.file.Path

import slicer.harness.{Corpus, ScalaCompiler, Slice, Workspace}
import slicer.model.{DefNode, SliceOptions, Symbol}

import cats.effect.syntax.concurrent.*
import cats.effect.{IO, Resource}
import cats.syntax.apply.*
import cats.syntax.eq.*

final class CompilesEveryDefinition(corpus: Corpus) {

  lazy val roots: Vector[DefNode] = corpus.allSymbols

  def compileEach: IO[Map[Symbol, String]] =
    (workspace, compiler).tupled.use { case (directory, compiler) =>
      for {
        distinct <- IO(distinctSlices)
        failures <- distinct.parTraverseN(CompilesEveryDefinition.compilersAtOnce) { case ((root, slice), sharing) =>
          compileStandalone(compiler = compiler, directory = directory, root = root, slice = slice, sharing = sharing)
        }
      } yield failures.flatten.toMap
    }

  private val workspace: Resource[IO, Path] =
    Resource.make(IO.blocking(Workspace.create("slice-corpus-")))(directory => IO.blocking(Workspace.delete(directory)))

  private val compiler: Resource[IO, ScalaCompiler] =
    Resource.fromAutoCloseable(IO(ScalaCompiler.forCorpus(corpus)))

  private def distinctSlices: Vector[((DefNode, Slice), Vector[DefNode])] =
    roots
      .map(root => root -> corpus.sliceOf(root, SliceOptions(followImplementations = true, keepFields = false)))
      .groupBy { case (_, slice) => slice.text }
      .toVector
      .map { case (_, sharing) => sharing.minBy { case (root, _) => root.symbol } -> sharing.map(_._1) }

  private def compileStandalone(
      compiler: ScalaCompiler,
      directory: Path,
      root: DefNode,
      slice: Slice,
      sharing: Vector[DefNode]
  ): IO[Vector[(Symbol, String)]] =
    IO.blocking {
      val written = slice.writeTo(directory.resolve(root.symbol.toDirectoryName))
      compiler.compileDirectory(written, slice.compiledFirst.map(written.resolve))
    }.map(outcome =>
      if (outcome.ok) Vector.empty
      else
        sharing
          .map(shared => shared.symbol -> failureFor(root = root, shared = shared, errorReport = outcome.errorReport))
    )

  private def failureFor(root: DefNode, shared: DefNode, errorReport: String): String =
    if (shared === root) s"=== ${root.dottedName}\n$errorReport"
    else s"=== ${shared.dottedName}, which slices to the same sources as ${root.dottedName}\n$errorReport"
}

object CompilesEveryDefinition {

  private val compilersAtOnce: Int = math.max(1, Runtime.getRuntime.availableProcessors)
}
