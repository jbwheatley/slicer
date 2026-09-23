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

package slicer.compat

import java.nio.file.Path

private[slicer] object SliceArgumentFormat {

  val sourceRootKey: String = "source-root"
  val outKey: String = "out"
  val semanticdbKey: String = "semanticdb"
  val sourceDirKey: String = "source-dir"
  val scalaVersionKey: String = "scala-version"
  val toolKey: String = "tool"
  val toolVersionKey: String = "tool-version"
  val dependencyKey: String = "dependency"
  val scalacOptionKey: String = "scalac-option"
  val platformKey: String = "platform"
  val platformVersionKey: String = "platform-version"
  val queryKey: String = "query"
  val followImplementationsKey: String = "follow-implementations"
  val keepFieldsKey: String = "keep-fields"

  val sbtTool: String = "sbt"
  val millTool: String = "mill"

  val jvmPlatform: String = "jvm"
  val scalaJsPlatform: String = "scala-js"
  val scalaNativePlatform: String = "scala-native"

  val dependencyFieldSeparator: String = "|"

  val disabledCrossVersion: String = "Disabled"
  val binaryCrossVersion: String = "Binary"
  val fullCrossVersion: String = "Full"

  val compileScope: String = "Compile"
  val providedScope: String = "Provided"
  val pluginScope: String = "Plugin"

  def renderField(key: String, value: String): String = s"$key=$value"

  def renderRequestFields(
      sourceRoot: Path,
      out: Path,
      tool: String,
      toolVersion: String,
      scalaVersion: String,
      platform: String,
      platformVersion: String,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      dependencies: Vector[String],
      scalacOptions: Vector[String]
  ): Vector[String] =
    Vector(
      renderField(sourceRootKey, sourceRoot.toString),
      renderField(outKey, out.toString),
      renderField(toolKey, tool),
      renderField(toolVersionKey, toolVersion),
      renderField(scalaVersionKey, scalaVersion),
      renderField(platformKey, platform),
      renderField(platformVersionKey, platformVersion)
    ) ++
      semanticdbDirs.map(directory => renderField(semanticdbKey, directory.toString)) ++
      sourceDirs.map(directory => renderField(sourceDirKey, directory.toString)) ++
      dependencies.map(dependency => renderField(dependencyKey, dependency)) ++
      scalacOptions.map(option => renderField(scalacOptionKey, option))
}
