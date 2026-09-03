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

package slicer.sbt

import java.nio.file.{Path, Paths}

import slicer.model.{CrossVersion as SliceCrossVersion, Platform as SlicePlatform, *}

import _root_.sbt.*

class SbtSliceInputsSuite extends munit.FunSuite {

  private val corpus: Path = Paths.get(sys.props("slicer.sbtCorpus"))

  private val modules = Vector("base", "external", "entry")

  private val semanticdbDirs: Vector[Path] =
    modules.map(module => corpus.resolve(s"target/out/jvm/scala-3.8.4/$module/meta"))

  private val sourceDirs: Vector[Path] =
    modules.map(module => corpus.resolve(s"$module/src/main/scala"))

  private def inputsOf(semanticdbDirs: Vector[Path], sourceDirs: Vector[Path]) =
    SbtSliceInputs.buildSliceInputs(
      sourceRoot = corpus,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      out = corpus.resolve("target/slice"),
      scalaVersion = "3.8.4",
      sbtVersion = "2.0.6",
      dependencies = Vector.empty,
      scalacOptions = Vector.empty,
      platform = SlicePlatform.Jvm
    )

  test("a cross-versioned dependency keeps its cross-version, a java one does not") {
    assertEquals(
      SbtSliceInputs.toDependency("org.typelevel" %% "cats-core" % "2.13.0", SlicePlatform.Jvm),
      Dependency("org.typelevel", "cats-core", "2.13.0", SliceCrossVersion.Binary, DependencyScope.Compile, false)
    )
    assertEquals(
      SbtSliceInputs.toDependency("com.lihaoyi" % "os-lib" % "0.9.0", SlicePlatform.Jvm),
      Dependency("com.lihaoyi", "os-lib", "0.9.0", SliceCrossVersion.Disabled, DependencyScope.Compile, false)
    )
  }

  test("a dependency cross-versioned with a platform prefix is read as platformed") {
    val onScalaJs =
      ("org.typelevel" % "cats-core" % "2.13.0").cross(CrossVersion.binaryWith("sjs1_", ""))
    assertEquals(
      SbtSliceInputs.toDependency(onScalaJs, SlicePlatform.Jvm),
      Dependency("org.typelevel", "cats-core", "2.13.0", SliceCrossVersion.Binary, DependencyScope.Compile, true)
    )
  }

  test("a cross-versioned dependency of a project off the JVM resolves on that project's platform") {
    assertEquals(
      SbtSliceInputs
        .toDependency("org.typelevel" %% "cats-core" % "2.13.0", SlicePlatform.ScalaJs("1.22.0"))
        .platformed,
      true
    )
    assertEquals(
      SbtSliceInputs.toDependency("com.lihaoyi" % "os-lib" % "0.9.0", SlicePlatform.ScalaJs("1.22.0")).platformed,
      false
    )
  }

  test("the platform is read off the library the platform's own plugin put in the build") {
    assertEquals(
      SbtSliceInputs.detectPlatform(Seq("org.scala-js" %% "scalajs-library" % "1.19.0")),
      SlicePlatform.ScalaJs("1.19.0")
    )
    assertEquals(
      SbtSliceInputs.detectPlatform(Seq("org.scala-native" %% "nativelib" % "0.5.8")),
      SlicePlatform.ScalaNative("0.5.8")
    )
    assertEquals(SbtSliceInputs.detectPlatform(Seq("org.typelevel" %% "cats-core" % "2.13.0")), SlicePlatform.Jvm)
  }

  test("the dependencies of every project collapse into one sorted list") {
    val modules = Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
      "com.lihaoyi" % "os-lib" % "0.9.0",
      "org.typelevel" %% "cats-core" % "2.13.0"
    )

    assertEquals(
      SbtSliceInputs
        .collectDependencies(modules, SlicePlatform.Jvm)
        .map(dependency => (dependency.organization, dependency.artifact)),
      Vector(("com.lihaoyi", "os-lib"), ("org.typelevel", "cats-core"))
    )
  }

  test("a test dependency is left out of the slice's build") {
    val modules = Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.scalameta" %% "munit" % "1.3.5" % "test"
    )

    assertEquals(SbtSliceInputs.collectDependencies(modules, SlicePlatform.Jvm).map(_.artifact), Vector("cats-core"))
  }

  test("a dependency compiled against but not published with keeps the scope it was declared in") {
    val modules = Seq(
      "com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided,
      "org.typelevel" %% "cats-core" % "2.13.0" % Optional,
      compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))
    )

    assertEquals(
      SbtSliceInputs
        .collectDependencies(modules, SlicePlatform.Jvm)
        .map(dependency => (dependency.artifact, dependency.scope)),
      Vector(
        ("sourcecode", DependencyScope.Provided),
        ("cats-core", DependencyScope.Provided),
        ("kind-projector", DependencyScope.Plugin)
      )
    )
  }

  test("a fully cross-versioned dependency keeps its full Scala version") {
    assertEquals(
      SbtSliceInputs
        .toDependency(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full), SlicePlatform.Jvm)
        .crossVersion,
      SliceCrossVersion.Full
    )
  }

  test("inputs built from a project's own paths carry an sbt build and the index of its sources") {
    inputsOf(semanticdbDirs, sourceDirs) match {
      case Left(error) => fail(error.getMessage)
      case Right(inputs) =>
        assertEquals(inputs.sourceRoot, corpus)
        assertEquals(inputs.tool, BuildTool.Sbt("3.8.4", "2.0.6", Vector.empty, Vector.empty, SlicePlatform.Jvm))
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

  test("projects with SemanticDB off are named, and one with it on is not complained about") {
    assertEquals(
      SbtSliceInputs.findProjectsMissingSemanticdb(Seq("base" -> true, "entry" -> false, "external" -> false)),
      Some("slice reads SemanticDB; these projects have it off: entry, external")
    )
    assertEquals(SbtSliceInputs.findProjectsMissingSemanticdb(Seq("base" -> true)), None)
  }
}
