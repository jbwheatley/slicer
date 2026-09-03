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

import java.nio.file.{Path, Paths}

import slicer.model.{Dependency as SliceDependency, *}

import mill.javalib.Dep
import mill.scalalib.*

class MillSliceInputsSuite extends munit.FunSuite {

  private val corpus: Path = Paths.get(sys.props("slicer.millCorpus"))

  private val semanticdbDirs: Vector[Path] =
    Vector("base", "external", "entry").map(module => corpus.resolve(s"out/$module/semanticDbDataDetailed.dest/data"))

  private val sourceDirs: Vector[Path] =
    Vector("base", "external", "entry").map(module => corpus.resolve(s"$module/src/main/scala"))

  private def inputsOf(semanticdbDirs: Vector[Path], sourceDirs: Vector[Path]) =
    MillSliceInputs.buildSliceInputs(
      sourceRoot = corpus,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      out = corpus.resolve("out/slice"),
      tool = BuildTool.Mill(
        scalaVersion = "3.3.6",
        millVersion = "1.1.8",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      )
    )

  test("a cross-versioned dependency keeps its cross-version, a java one does not") {
    assertEquals(
      MillSliceInputs.toDependency(mvn"org.typelevel::cats-core:2.13.0", DependencyScope.Compile),
      SliceDependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, false)
    )
    assertEquals(
      MillSliceInputs.toDependency(mvn"com.lihaoyi:os-lib:0.9.0", DependencyScope.Compile),
      SliceDependency("com.lihaoyi", "os-lib", "0.9.0", CrossVersion.Disabled, DependencyScope.Compile, false)
    )
  }

  test("a fully cross-versioned plugin dependency keeps its full Scala version and its scope") {
    assertEquals(
      MillSliceInputs.toDependency(mvn"org.typelevel:::kind-projector:0.13.3", DependencyScope.Plugin),
      SliceDependency("org.typelevel", "kind-projector", "0.13.3", CrossVersion.Full, DependencyScope.Plugin, false)
    )
  }

  test("a dependency mill cross-versions with a platform is read as platformed") {
    assertEquals(
      MillSliceInputs.toDependency(mvn"org.typelevel::cats-core::2.13.0", DependencyScope.Compile),
      SliceDependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, true)
    )
  }

  test("the dependencies of every module collapse into one sorted list") {
    val deps: Seq[Dep] =
      Seq(mvn"org.typelevel::cats-core:2.13.0", mvn"com.lihaoyi:os-lib:0.9.0", mvn"org.typelevel::cats-core:2.13.0")

    assertEquals(
      MillSliceInputs
        .collectDependencies(deps, DependencyScope.Compile)
        .map(dependency => (dependency.organization, dependency.artifact)),
      Vector(("com.lihaoyi", "os-lib"), ("org.typelevel", "cats-core"))
    )
  }

  test("inputs built from a module's own paths carry a mill build and the index of its sources") {
    inputsOf(semanticdbDirs, sourceDirs) match {
      case Left(error) => fail(error.getMessage)
      case Right(inputs) =>
        assertEquals(inputs.sourceRoot, corpus)
        assertEquals(inputs.tool, BuildTool.Mill("3.3.6", "1.1.8", Vector.empty, Vector.empty, Platform.Jvm))
        assert(inputs.index.defs.nonEmpty, "the corpus index came back empty")
    }
  }

  test("inputs built from a Scala 2 module carry its Scala version and the index of its sources") {
    val corpus213: Path = Paths.get(sys.props("slicer.millCorpus213"))
    val modules = Vector("base", "external", "entry")

    MillSliceInputs.buildSliceInputs(
      sourceRoot = corpus213,
      semanticdbDirs = modules.map(module => corpus213.resolve(s"out/$module/semanticDbDataDetailed.dest/data")),
      sourceDirs = modules.map(module => corpus213.resolve(s"$module/src/main/scala")),
      out = corpus213.resolve("out/slice"),
      tool = BuildTool.Mill(
        scalaVersion = "2.13.16",
        millVersion = "1.1.8",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      )
    ) match {
      case Left(error) => fail(error.getMessage)
      case Right(inputs) =>
        assertEquals(inputs.tool, BuildTool.Mill("2.13.16", "1.1.8", Vector.empty, Vector.empty, Platform.Jvm))
        assert(inputs.index.defs.nonEmpty, "the Scala 2 corpus index came back empty")
    }
  }

  test("a build that never emitted SemanticDB is reported with the task that emits it") {
    inputsOf(Vector.empty, sourceDirs) match {
      case Left(error) => assert(error.getMessage.contains("semanticDbData"), error.getMessage)
      case Right(_)    => fail("expected an error naming the mill task to run")
    }
  }

  test("SemanticDB that holds no definitions is reported rather than picked over") {
    inputsOf(Vector(corpus.resolve("out/mill-build")), sourceDirs) match {
      case Left(error) => assert(error.getMessage.contains("no definitions"), error.getMessage)
      case Right(_)    => fail("expected an error about an index with nothing in it")
    }
  }
}
