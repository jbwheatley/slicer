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

import sbt.*

class SbtSliceRequestSuite extends munit.FunSuite {

  private val jvm = DetectedPlatform.Jvm
  private val scalaJs = DetectedPlatform.ScalaJs("1.22.0")

  private val sbt2 = "2.0.6"
  private val sbt1 = "1.12.3"

  test("a cross-versioned dependency keeps its cross-version, a java one does not") {
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(module = "org.typelevel" %% "cats-core" % "2.13.0", platform = jvm, sbtVersion = sbt2),
      expected = "org.typelevel|cats-core|2.13.0|Binary|Compile|false"
    )
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(module = "com.lihaoyi" % "os-lib" % "0.9.0", platform = jvm, sbtVersion = sbt2),
      expected = "com.lihaoyi|os-lib|0.9.0|Disabled|Compile|false"
    )
  }

  test("a fully cross-versioned dependency keeps its full Scala version") {
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(
          module = ("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full),
          platform = jvm,
          sbtVersion = sbt2
        ),
      expected = "org.typelevel|kind-projector|0.13.3|Full|Compile|false"
    )
  }

  test("a dependency cross-versioned with a platform prefix is read as platformed") {
    val onScalaJs = ("org.typelevel" % "cats-core" % "2.13.0").cross(CrossVersion.binaryWith("sjs1_", ""))

    assertEquals(
      obtained = SbtSliceRequest.toDependencyText(module = onScalaJs, platform = jvm, sbtVersion = sbt1),
      expected = "org.typelevel|cats-core|2.13.0|Binary|Compile|true"
    )
  }

  test("on sbt 2 a cross-versioned dependency of a project off the JVM resolves on that project's platform") {
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(module = "org.typelevel" %% "cats-core" % "2.13.0", platform = scalaJs, sbtVersion = sbt2),
      expected = "org.typelevel|cats-core|2.13.0|Binary|Compile|true"
    )
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(module = "com.lihaoyi" % "os-lib" % "0.9.0", platform = scalaJs, sbtVersion = sbt2),
      expected = "com.lihaoyi|os-lib|0.9.0|Disabled|Compile|false"
    )
  }

  test("on sbt 1 a cross-versioned dependency without a platform prefix resolves on the JVM") {
    assertEquals(
      obtained = SbtSliceRequest
        .toDependencyText(module = "org.typelevel" %% "cats-core" % "2.13.0", platform = scalaJs, sbtVersion = sbt1),
      expected = "org.typelevel|cats-core|2.13.0|Binary|Compile|false"
    )
  }

  test("the platform is read off the library the platform's own plugin put in the build") {
    assertEquals(
      obtained = SbtSliceRequest.detectPlatform(Seq("org.scala-js" %% "scalajs-library" % "1.19.0")),
      expected = DetectedPlatform.ScalaJs("1.19.0")
    )
    assertEquals(
      obtained = SbtSliceRequest.detectPlatform(Seq("org.scala-native" %% "nativelib" % "0.5.8")),
      expected = DetectedPlatform.ScalaNative("0.5.8")
    )
    assertEquals(
      obtained = SbtSliceRequest.detectPlatform(Seq("org.typelevel" %% "cats-core" % "2.13.0")),
      expected = jvm
    )
  }

  test("the dependencies of every project collapse into one sorted list") {
    val modules = Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
      "com.lihaoyi" % "os-lib" % "0.9.0",
      "org.typelevel" %% "cats-core" % "2.13.0"
    )

    assertEquals(
      obtained = SbtSliceRequest.collectDependencies(modules = modules, platform = jvm, sbtVersion = sbt2),
      expected =
        Vector("com.lihaoyi|os-lib|0.9.0|Disabled|Compile|false", "org.typelevel|cats-core|2.13.0|Binary|Compile|false")
    )
  }

  test("a test dependency is left out of the slice's build") {
    val modules = Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.scalameta" %% "munit" % "1.3.5" % "test"
    )

    assertEquals(
      obtained = SbtSliceRequest.collectDependencies(modules = modules, platform = jvm, sbtVersion = sbt2),
      expected = Vector("org.typelevel|cats-core|2.13.0|Binary|Compile|false")
    )
  }

  test("a dependency compiled against but not published with keeps the scope it was declared in") {
    val modules = Seq(
      "com.lihaoyi" %% "sourcecode" % "0.4.2" % Provided,
      "org.typelevel" %% "cats-core" % "2.13.0" % Optional,
      compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))
    )

    assertEquals(
      obtained = SbtSliceRequest.collectDependencies(modules = modules, platform = jvm, sbtVersion = sbt2),
      expected = Vector(
        "com.lihaoyi|sourcecode|0.4.2|Binary|Provided|false",
        "org.typelevel|cats-core|2.13.0|Binary|Provided|false",
        "org.typelevel|kind-projector|0.13.3|Full|Plugin|false"
      )
    )
  }

  test("projects with SemanticDB off are named, and one with it on is not complained about") {
    assertEquals(
      obtained =
        SbtSliceRequest.findProjectsMissingSemanticdb(Seq("base" -> true, "entry" -> false, "external" -> false)),
      expected = Some("slice reads SemanticDB; these projects have it off: entry, external")
    )
    assertEquals(obtained = SbtSliceRequest.findProjectsMissingSemanticdb(Seq("base" -> true)), expected = None)
  }
}
