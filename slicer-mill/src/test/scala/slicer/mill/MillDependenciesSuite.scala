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

import slicer.model.{Dependency as SliceDependency, *}

import mill.javalib.Dep
import mill.scalalib.*

class MillDependenciesSuite extends munit.FunSuite {

  test("a cross-versioned dependency keeps its cross-version, a java one does not") {
    assertEquals(
      MillDependencies.toDependency(mvn"org.typelevel::cats-core:2.13.0", DependencyScope.Compile),
      SliceDependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, false)
    )
    assertEquals(
      MillDependencies.toDependency(mvn"com.lihaoyi:os-lib:0.9.0", DependencyScope.Compile),
      SliceDependency("com.lihaoyi", "os-lib", "0.9.0", CrossVersion.Disabled, DependencyScope.Compile, false)
    )
  }

  test("a fully cross-versioned plugin dependency keeps its full Scala version and its scope") {
    assertEquals(
      MillDependencies.toDependency(mvn"org.typelevel:::kind-projector:0.13.3", DependencyScope.Plugin),
      SliceDependency("org.typelevel", "kind-projector", "0.13.3", CrossVersion.Full, DependencyScope.Plugin, false)
    )
  }

  test("a dependency mill cross-versions with a platform is read as platformed") {
    assertEquals(
      MillDependencies.toDependency(mvn"org.typelevel::cats-core::2.13.0", DependencyScope.Compile),
      SliceDependency("org.typelevel", "cats-core", "2.13.0", CrossVersion.Binary, DependencyScope.Compile, true)
    )
  }

  test("the dependencies of every module collapse into one sorted list") {
    val deps: Seq[Dep] =
      Seq(mvn"org.typelevel::cats-core:2.13.0", mvn"com.lihaoyi:os-lib:0.9.0", mvn"org.typelevel::cats-core:2.13.0")

    assertEquals(
      MillDependencies
        .collectDependencies(deps, DependencyScope.Compile)
        .map(dependency => (dependency.organization, dependency.artifact)),
      Vector(("com.lihaoyi", "os-lib"), ("org.typelevel", "cats-core"))
    )
  }
}
