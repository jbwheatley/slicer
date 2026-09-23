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

import slicer.model.Dependency

import lmcoursier.FromSbt
import sbt.*
import sbt.librarymanagement.Platform

class SbtSliceRequestCoordinateSuite extends munit.FunSuite {

  private val sbtVersion = "2.0.8"
  private val scalaVersion = "3.8.4"

  private val pinned = ("org.typelevel" %% "cats-core" % "2.13.0").cross(CrossVersion.for3Use2_13)

  private def resolveArtifactName(module: ModuleID, projectPlatform: String): String = {
    val (resolved, _) = FromSbt.moduleVersion(
      module,
      scalaVersion,
      CrossVersion.binaryScalaVersion(scalaVersion),
      false,
      Some(projectPlatform)
    )
    resolved.name.value
  }

  private def renderArtifactName(module: ModuleID, platform: DetectedPlatform): String =
    Dependency
      .parse(
        SbtSliceRequest
          .toDependencyText(module = module, platform = platform, sbtVersion = sbtVersion, scalaVersion = scalaVersion)
      )
      .fold(failure => fail(failure.toString), dependency => dependency.artifact)

  test("a pinned artifact of a Scala.js project is sent under the name sbt 2 resolves it by") {
    assertEquals(
      obtained = renderArtifactName(module = pinned, platform = DetectedPlatform.ScalaJs("1.22.0")),
      expected = resolveArtifactName(module = pinned, projectPlatform = Platform.sjs1)
    )
  }

  test("a pinned artifact of a Scala Native project is sent under the name sbt 2 resolves it by") {
    assertEquals(
      obtained = renderArtifactName(module = pinned, platform = DetectedPlatform.ScalaNative("0.4.17")),
      expected = resolveArtifactName(module = pinned, projectPlatform = Platform.native0_4)
    )
  }

  test("a pinned artifact of a JVM project is sent under the name sbt 2 resolves it by") {
    assertEquals(
      obtained = renderArtifactName(module = pinned, platform = DetectedPlatform.Jvm),
      expected = resolveArtifactName(module = pinned, projectPlatform = Platform.jvm)
    )
  }
}
