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

import slicer.model.{Platform, SliceOptions}

import mill.api.Task
import mill.api.Task.Simple
import mill.api.{Discover, PathRef}
import mill.scalajslib.ScalaJSModule
import mill.scalalib.*
import mill.scalanativelib.ScalaNativeModule
import mill.testkit.{TestRootModule, UnitTester}
import mill.util.TokenReaders.*

class SlicerModuleSuite extends munit.FunSuite {

  private val scalaOfProject = "3.3.6"

  private val project: os.Path = os.Path(sys.props("slicer.unitProject"))

  private def fieldsOf[E](evaluated: Either[E, UnitTester.Result[Vector[String]]]): SliceArguments.Fields =
    evaluated match {
      case Left(failure) => fail(failure.toString)
      case Right(result) => SliceArguments.toFields(result.value).fold(failure => fail(failure.toString), identity)
    }

  test("the request handed to the picker carries the module's sources, semanticdb and dependencies") {
    object build extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      override def mvnDeps: Simple[Seq[Dep]] = Seq(mvn"org.typelevel::cats-core:2.13.0")
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(build, project).scoped { eval =>
      val fields = fieldsOf(eval(build.sliceArguments("Greeting")))
      val tool = SliceArguments.readBuildTool(fields).fold(failure => fail(failure.toString), identity)
      val semanticdbDirs = SliceArguments.readSemanticdbDirs(fields).fold(failure => fail(failure.toString), identity)
      val sourceDirs = SliceArguments.readSourceDirs(fields).fold(failure => fail(failure.toString), identity)

      assertEquals(SliceArguments.readQuery(fields), "Greeting")
      assertEquals(tool.scalaVersion, scalaOfProject)
      assertEquals(
        tool.dependencies.map(dependency => (dependency.organization, dependency.artifact)).toList,
        List(("org.typelevel", "cats-core"))
      )
      assert(semanticdbDirs.nonEmpty, fields.toString)
      assert(sourceDirs.exists(directory => directory.endsWith("src")), sourceDirs.toString)
    }
  }

  test("the sources a module generates are handed over beside the ones it was written with") {
    object build extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      override def mvnDeps: Simple[Seq[Dep]] = Seq(mvn"org.typelevel::cats-core:2.13.0")
      override def generatedSources: Simple[Seq[PathRef]] = Task {
        os.write.over(Task.dest / "Generated.scala", "object Generated\n", createFolders = true)
        Seq(PathRef(Task.dest))
      }
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(build, project).scoped { eval =>
      val sourceDirs = SliceArguments
        .readSourceDirs(fieldsOf(eval(build.sliceArguments(""))))
        .fold(failure => fail(failure.toString), identity)

      assert(
        sourceDirs.exists(directory => directory.toString.contains("generatedSources.dest")),
        sourceDirs.toString
      )
    }
  }

  test("the platform a module builds on is the platform its request carries") {
    object onScalaJs extends TestRootModule with SlicerModule with ScalaJSModule {
      override def scalaVersion = scalaOfProject
      override def scalaJSVersion = "1.22.0"
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    object onScalaNative extends TestRootModule with SlicerModule with ScalaNativeModule {
      override def scalaVersion = scalaOfProject
      override def scalaNativeVersion = "0.5.12"
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    object onTheJvm extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(onScalaJs, project).scoped { eval =>
      eval(onScalaJs.slicePlatform()) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => assertEquals(result.value, Platform.ScalaJs("1.22.0"))
      }
    }

    UnitTester(onScalaNative, project).scoped { eval =>
      eval(onScalaNative.slicePlatform()) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => assertEquals(result.value, Platform.ScalaNative("0.5.12"))
      }
    }

    UnitTester(onTheJvm, project).scoped { eval =>
      eval(onTheJvm.slicePlatform()) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => assertEquals(result.value, Platform.Jvm)
      }
    }
  }

  test("a Scala 2 module emits SemanticDB with the synthetics a Scala 3 one has no need of") {
    object scala2 extends TestRootModule with SlicerModule {
      override def scalaVersion = "2.13.16"
      def semanticdbScalacOptions: Simple[Seq[String]] = Task { semanticDbEnablePluginScalacOptions() }
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    object scala3 extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      def semanticdbScalacOptions: Simple[Seq[String]] = Task { semanticDbEnablePluginScalacOptions() }
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(scala2, project).scoped { eval =>
      eval(scala2.semanticdbScalacOptions) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => assert(result.value.contains("-P:semanticdb:synthetics:on"), result.value.toString)
      }
    }

    UnitTester(scala3, project).scoped { eval =>
      eval(scala3.semanticdbScalacOptions) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => assert(!result.value.contains("-P:semanticdb:synthetics:on"), result.value.toString)
      }
    }
  }

  test("the options a module overrides are the options the picker opens on") {
    val chosen = SliceOptions(followImplementations = false, keepFields = true)
    object build extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      override def mvnDeps: Simple[Seq[Dep]] = Seq(mvn"org.typelevel::cats-core:2.13.0")
      override def sliceOptions: SliceOptions = chosen
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(build, project).scoped { eval =>
      assertEquals(SliceArguments.readOptions(fieldsOf(eval(build.sliceArguments("")))), Right(chosen))
    }
  }

  test("sliceClear removes the slices under the module's slice destination") {
    object build extends TestRootModule with SlicerModule {
      override def scalaVersion = scalaOfProject
      lazy override val millDiscover: Discover = Discover[this.type]
    }

    UnitTester(build, project).scoped { eval =>
      val destination = eval(build.sliceDestination) match {
        case Left(failure) => fail(failure.toString)
        case Right(result) => result.value
      }
      val written = destination / "spec-Greeting-"
      os.write.over(written / "src/main/scala/Greeting.scala", "object Greeting\n", createFolders = true)

      eval(build.sliceClear()) match {
        case Left(failure) => fail(failure.toString)
        case Right(_)      => ()
      }

      assert(!os.exists(written), s"$written survived sliceClear")
      assert(os.exists(destination), s"$destination should outlive the slices in it")
    }
  }
}
