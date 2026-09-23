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

import slicer.compat.ArgumentUtil
import slicer.model.{CrossVersion as SliceCrossVersion, Platform as SlicePlatform, *}

import sbt.*

class SbtSliceRequestReadBackSuite extends munit.FunSuite {

  private val out: Path = Paths.get("/work/project/target/slice")

  private val modules = Seq(
    "org.typelevel" %% "cats-core" % "2.13.0",
    "com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided,
    compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)),
    "org.scala-js" %% "scalajs-library" % "1.19.0"
  )

  private def render(sbtVersion: String): Vector[String] =
    SbtSliceRequest.renderAsArgs(
      sourceRoot = Paths.get("/work/project"),
      out = out,
      semanticdbDirs = Vector(Paths.get("/work/project/target/meta")),
      sourceDirs = Vector(Paths.get("/work/project/src/main/scala")),
      scalaVersion = "3.8.4",
      sbtVersion = sbtVersion,
      modules = modules,
      scalacOptions = Vector("-Xkind-projector")
    )

  private def fieldsOf(args: Vector[String]): SliceArguments.Fields =
    SliceArguments.toFields(args).fold(failure => fail(failure.toString), identity)

  test("the arguments the plugin renders read back as the sbt build they describe") {
    assertEquals(
      SliceArguments.readBuildTool(fieldsOf(render("2.0.8"))),
      Right(
        BuildTool.Sbt(
          scalaVersion = "3.8.4",
          sbtVersion = "2.0.8",
          dependencies = Vector(
            Dependency("com.lihaoyi", "sourcecode", "0.4.2", SliceCrossVersion.Binary, DependencyScope.Provided, true),
            Dependency(
              "org.scala-js",
              "scalajs-library",
              "1.19.0",
              SliceCrossVersion.Binary,
              DependencyScope.Compile,
              true
            ),
            Dependency("org.typelevel", "cats-core", "2.13.0", SliceCrossVersion.Binary, DependencyScope.Compile, true),
            Dependency(
              "org.typelevel",
              "kind-projector",
              "0.13.3",
              SliceCrossVersion.Full,
              DependencyScope.Plugin,
              false
            )
          ),
          scalacOptions = Vector("-Xkind-projector"),
          platform = SlicePlatform.ScalaJs("1.19.0")
        )
      )
    )
  }

  test("the query the slice command names is the one the picker opens on, with the default options") {
    val fields = fieldsOf(render("1.12.3") :+ ArgumentUtil.renderQuery("spec.external.CallsLibrary"))

    assertEquals(SliceArguments.readQuery(fields), "spec.external.CallsLibrary")
    assertEquals(SliceArguments.readOptions(fields), Right(SliceOptions.default))
  }
}
