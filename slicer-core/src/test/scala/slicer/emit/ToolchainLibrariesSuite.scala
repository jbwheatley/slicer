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

package slicer.emit

import slicer.model.{CrossVersion, Dependency, DependencyScope}

class ToolchainLibrariesSuite extends munit.FunSuite {

  private def dependency(organization: String, artifact: String, scope: DependencyScope): Dependency =
    Dependency(
      organization = organization,
      artifact = artifact,
      version = "1.0.0",
      crossVersion = CrossVersion.Binary,
      scope = scope,
      platformed = true
    )

  test("the standard library comes with the scala version, not with a dependency") {
    val dependencies = Vector(
      dependency(organization = "org.scala-lang", artifact = "scala3-library", scope = DependencyScope.Compile),
      dependency(organization = "com.lihaoyi", artifact = "os-lib", scope = DependencyScope.Compile)
    )
    assertEquals(ToolchainLibraries.dropToolchainLibraries(dependencies).map(_.artifact), Vector("os-lib"))
  }

  test("the platform plugin brings its own runtime, so the slice does not ask for it") {
    val dependencies = Vector(
      dependency(organization = "org.scala-native", artifact = "nativelib", scope = DependencyScope.Compile),
      dependency(organization = "org.scala-native", artifact = "nscplugin", scope = DependencyScope.Plugin),
      dependency(organization = "org.scala-js", artifact = "scalajs-library", scope = DependencyScope.Compile),
      dependency(organization = "org.scala-js", artifact = "scalajs-dom", scope = DependencyScope.Compile)
    )
    assertEquals(ToolchainLibraries.dropToolchainLibraries(dependencies).map(_.artifact), Vector("scalajs-dom"))
  }
}
