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

package slicer.model

class DependencySuite extends munit.FunSuite {

  private val cats =
    Dependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, platformed = false)

  private val osLib =
    Dependency("com.lihaoyi", "os-lib", "0.9.0", CrossVersion.Disabled, DependencyScope.Compile, platformed = false)

  private val sourcecode =
    Dependency("com.lihaoyi", "sourcecode", "0.4.2", CrossVersion.Binary, DependencyScope.Provided, platformed = false)

  private val kindProjector =
    Dependency(
      "org.typelevel",
      "kind-projector",
      "0.13.3",
      CrossVersion.Full,
      DependencyScope.Plugin,
      platformed = false
    )

  private val catsOnJs = cats.copy(platformed = true)

  private val scalaJs = Platform.ScalaJs("1.19.0")

  private val scalaNative = Platform.ScalaNative("0.5.8")

  test("a dependency renders in the syntax of the build tool that will read it") {
    assertEquals(
      cats.renderSbtSyntax(Platform.Jvm, platformAppliedByBuild = true),
      """"org.typelevel" %% "cats-core" % "2.13.0""""
    )
    assertEquals(
      osLib.renderSbtSyntax(Platform.Jvm, platformAppliedByBuild = true),
      """"com.lihaoyi" % "os-lib" % "0.9.0""""
    )
    assertEquals(cats.renderMillSyntax, """mvn"org.typelevel::cats-core:2.13.0"""")
    assertEquals(osLib.renderMillSyntax, """mvn"com.lihaoyi:os-lib:0.9.0"""")
  }

  test("a platformed dependency asks mill for the artifact built for that platform") {
    assertEquals(catsOnJs.renderMillSyntax, """mvn"org.typelevel::cats-core::2.13.0"""")
    assertEquals(osLib.copy(platformed = true).renderMillSyntax, """mvn"com.lihaoyi:os-lib::0.9.0"""")
    assertEquals(
      kindProjector.copy(platformed = true, scope = DependencyScope.Compile).renderMillSyntax,
      """mvn"org.typelevel:::kind-projector::0.13.3""""
    )
  }

  test("an sbt build that cross-versions with the platform itself is left to do it") {
    assertEquals(
      catsOnJs.renderSbtSyntax(scalaJs, platformAppliedByBuild = true),
      """"org.typelevel" %% "cats-core" % "2.13.0""""
    )
    assertEquals(
      kindProjector
        .copy(platformed = true, scope = DependencyScope.Compile)
        .renderSbtSyntax(scalaNative, platformAppliedByBuild = true),
      """("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)"""
    )
  }

  test("an sbt build that leaves the platform to the dependency is given it spelled out") {
    assertEquals(
      catsOnJs.renderSbtSyntax(scalaJs, platformAppliedByBuild = false),
      """("org.typelevel" % "cats-core" % "2.13.0").cross(CrossVersion.binaryWith("sjs1_", ""))"""
    )
    assertEquals(
      kindProjector
        .copy(platformed = true, scope = DependencyScope.Compile)
        .renderSbtSyntax(scalaNative, platformAppliedByBuild = false),
      """("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.fullWith("native0.5_", ""))"""
    )
  }

  test("an artifact carrying the platform but no Scala version keeps it whichever sbt reads the build") {
    val onScalaJs = osLib.copy(platformed = true)
    val spelled = """("com.lihaoyi" % "os-lib" % "0.9.0").cross(CrossVersion.constant("sjs1"))"""
    assertEquals(onScalaJs.renderSbtSyntax(scalaJs, platformAppliedByBuild = true), spelled)
    assertEquals(onScalaJs.renderSbtSyntax(scalaJs, platformAppliedByBuild = false), spelled)
  }

  test("every field of a dependency survives being written down and read back") {
    Vector(cats, osLib, sourcecode, kindProjector, catsOnJs).foreach(dependency =>
      assertEquals(Dependency.parse(dependency.renderAsText), Right(dependency))
    )
  }

  test("a dependency written down carries no build-tool syntax with it") {
    assertEquals(cats.renderAsText, "org.typelevel|cats-core|2.13.0|Binary|Compile|false")
  }

  test("a dependency that cannot be read back is reported rather than guessed at") {
    assert(Dependency.parse("org.typelevel|cats-core|2.13.0").isLeft)
    assert(Dependency.parse("org.typelevel|cats-core|2.13.0|Sideways|Compile|false").isLeft)
    assert(Dependency.parse("org.typelevel|cats-core|2.13.0|Binary|Runtime|false").isLeft)
    assert(Dependency.parse("org.typelevel|cats-core|2.13.0|Binary|Compile|maybe").isLeft)
  }

  test("a platformed coordinate resolves the artifact built for the platform") {
    assertEquals(catsOnJs.toCoordinate("3.8.4", scalaJs), "org.typelevel:cats-core_sjs1_3:2.13.0")
    assertEquals(catsOnJs.toCoordinate("2.13.16", scalaNative), "org.typelevel:cats-core_native0.5_2.13:2.13.0")
    assertEquals(cats.toCoordinate("3.8.4", scalaJs), "org.typelevel:cats-core_3:2.13.0")
  }

  test("the scope a dependency was declared in shapes the syntax it renders in") {
    assertEquals(
      sourcecode.renderSbtSyntax(Platform.Jvm, platformAppliedByBuild = true),
      """"com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided"""
    )
    assertEquals(
      kindProjector.renderSbtSyntax(Platform.Jvm, platformAppliedByBuild = true),
      """compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))"""
    )
    assertEquals(sourcecode.renderMillSyntax, """mvn"com.lihaoyi::sourcecode:0.4.2"""")
    assertEquals(kindProjector.renderMillSyntax, """mvn"org.typelevel:::kind-projector:0.13.3"""")
  }

  test("a coordinate takes the Scala version its cross-versioning asks for") {
    assertEquals(cats.toCoordinate("3.8.4", Platform.Jvm), "org.typelevel:cats-core_3:2.13.0")
    assertEquals(osLib.toCoordinate("2.13.16", Platform.Jvm), "com.lihaoyi:os-lib:0.9.0")
    assertEquals(kindProjector.toCoordinate("2.13.16", Platform.Jvm), "org.typelevel:kind-projector_2.13.16:0.13.3")
  }

  test("dependencies in one scope come back without the others") {
    assertEquals(
      Dependency.filterToScope(Vector(cats, sourcecode, kindProjector), DependencyScope.Provided),
      Vector(sourcecode)
    )
  }
}
