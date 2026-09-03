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

import slicer.model.Dependency

private[emit] object ToolchainLibraries {

  private val supplied: Map[String, Set[String]] = Map(
    "org.scala-lang" -> Set("scala-library", "scala3-library", "scala3-compiler", "scala-compiler"),
    "org.scala-native" -> Set(
      "auxlib",
      "clib",
      "javalib",
      "junit-runtime",
      "nativelib",
      "nscplugin",
      "posixlib",
      "scala3lib",
      "scalalib",
      "test-interface",
      "windowslib"
    ),
    "org.scala-js" -> Set(
      "scalajs-compiler",
      "scalajs-javalib",
      "scalajs-junit-test-runtime",
      "scalajs-library",
      "scalajs-scalalib",
      "scalajs-test-bridge",
      "scalajs-test-interface"
    )
  )

  def dropToolchainLibraries(dependencies: Vector[Dependency]): Vector[Dependency] =
    dependencies.filterNot(dependency =>
      supplied.getOrElse(dependency.organization, Set.empty).contains(dependency.artifact)
    )
}
