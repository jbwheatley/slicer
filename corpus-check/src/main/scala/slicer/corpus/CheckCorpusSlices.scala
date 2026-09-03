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

import slicer.harness.{Corpus, TestProject, TestProject213}
import slicer.model.Symbol

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.foldable.*
import cats.syntax.traverse.*

object CheckCorpusSlices extends IOApp {

  private val corpuses: Vector[(String, Corpus)] =
    Vector("test-project" -> TestProject, "test-project-213" -> TestProject213)

  override def run(args: List[String]): IO[ExitCode] =
    corpuses
      .traverse { case (name, corpus) => checkCorpus(name, corpus) }
      .map(failures => if (failures.forall(_.isEmpty)) ExitCode.Success else ExitCode.Error)

  private def checkCorpus(name: String, corpus: Corpus): IO[Map[Symbol, String]] = {
    val definitions = new CompilesEveryDefinition(corpus)
    for {
      roots <- IO(definitions.roots.size)
      _ <- IO.println(s"$name: slicing and compiling $roots definitions")
      failures <- definitions.compileEach
      _ <- reportOn(name = name, roots = roots, failures = failures)
    } yield failures
  }

  private def reportOn(name: String, roots: Int, failures: Map[Symbol, String]): IO[Unit] =
    if (failures.isEmpty) IO.println(s"$name: every one of the $roots definitions slices into something that compiles")
    else
      failures.toVector.sortBy { case (symbol, _) => symbol }.traverse_ { case (_, report) => IO.println(report) } *>
        IO.println(s"$name: ${failures.size} of $roots definitions slice into something that does not compile")
}
