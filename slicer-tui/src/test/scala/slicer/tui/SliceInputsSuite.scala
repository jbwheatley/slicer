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

import slicer.model.{BuildTool, Platform}

class SliceInputsSuite extends munit.FunSuite {

  private val corpus: Path = Paths.get(sys.props("slicer.sbtCorpus"))

  private val modules = Vector("base", "external", "entry")

  private val semanticdbDirs: Vector[Path] =
    modules.map(module => corpus.resolve(s"target/out/jvm/scala-3.8.4/$module/meta"))

  private val sourceDirs: Vector[Path] =
    modules.map(module => corpus.resolve(s"$module/src/main/scala"))

  private val tool = BuildTool.Sbt("3.8.4", "2.0.6", Vector.empty, Vector.empty, Platform.Jvm)

  private def inputsOf(semanticdbDirs: Vector[Path], sourceDirs: Vector[Path]) =
    SliceInputs.build(
      sourceRoot = corpus,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      out = corpus.resolve("target/slice"),
      tool = tool
    )

  test("inputs built from a project's own paths carry an sbt build and the index of its sources") {
    inputsOf(semanticdbDirs, sourceDirs) match {
      case Left(error) => fail(error.getMessage)
      case Right(inputs) =>
        assertEquals(inputs.sourceRoot, corpus)
        assertEquals(inputs.tool, tool)
        assert(inputs.index.defs.nonEmpty, "the corpus index came back empty")
    }
  }

  test("a build that never emitted SemanticDB is reported with the setting that emits it") {
    inputsOf(Vector.empty, sourceDirs) match {
      case Left(error) => assert(error.getMessage.contains("semanticdbEnabled"), error.getMessage)
      case Right(_)    => fail("expected an error naming the setting to turn on")
    }
  }

  test("SemanticDB that holds no definitions is reported rather than picked over") {
    inputsOf(Vector(corpus.resolve("project/target")), sourceDirs) match {
      case Left(error) => assert(error.getMessage.contains("no definitions"), error.getMessage)
      case Right(_)    => fail("expected an error about an index with nothing in it")
    }
  }
}
