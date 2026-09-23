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

import java.nio.file.Path

import slicer.compat.SliceArgumentFormat.*
import slicer.compat.{PlatformToken, SbtVersionRules}

import cats.syntax.eq.*
import sbt.librarymanagement.{CrossVersion, ModuleID}

private[slicer] object SbtSliceRequest {

  def renderAsArgs(
      sourceRoot: Path,
      out: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      scalaVersion: String,
      sbtVersion: String,
      modules: Seq[ModuleID],
      scalacOptions: Vector[String]
  ): Vector[String] = {
    val platform = detectPlatform(modules)

    renderRequestFields(
      sourceRoot = sourceRoot,
      out = out,
      tool = sbtTool,
      toolVersion = sbtVersion,
      scalaVersion = scalaVersion,
      platform = toPlatformName(platform),
      platformVersion = toPlatformVersion(platform),
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      dependencies = collectDependencies(
        modules = modules,
        platform = platform,
        sbtVersion = sbtVersion,
        scalaVersion = scalaVersion
      ),
      scalacOptions = scalacOptions
    )
  }

  def renderQuery(query: String): String = renderField(queryKey, query)

  private def toPlatformName(platform: DetectedPlatform): String = platform match {
    case DetectedPlatform.Jvm            => jvmPlatform
    case _: DetectedPlatform.ScalaJs     => scalaJsPlatform
    case _: DetectedPlatform.ScalaNative => scalaNativePlatform
  }

  private def toPlatformVersion(platform: DetectedPlatform): String = platform match {
    case DetectedPlatform.Jvm                  => ""
    case DetectedPlatform.ScalaJs(version)     => version
    case DetectedPlatform.ScalaNative(version) => version
  }

  def detectPlatform(modules: Seq[ModuleID]): DetectedPlatform = {
    val scalaJs = modules.collectFirst {
      case module if module.organization === "org.scala-js" && module.name.startsWith("scalajs-library") =>
        DetectedPlatform.ScalaJs(module.revision)
    }
    val scalaNative = modules.collectFirst {
      case module if module.organization === "org.scala-native" && module.name.startsWith("nativelib") =>
        DetectedPlatform.ScalaNative(module.revision)
    }
    scalaJs.orElse(scalaNative).getOrElse(DetectedPlatform.Jvm)
  }

  def collectDependencies(
      modules: Seq[ModuleID],
      platform: DetectedPlatform,
      sbtVersion: String,
      scalaVersion: String
  ): Vector[String] =
    modules
      .filter(module => isSlicedConfiguration(module))
      .sortBy(module => (module.organization, module.name, module.revision))
      .map(module =>
        toDependencyText(module = module, platform = platform, sbtVersion = sbtVersion, scalaVersion = scalaVersion)
      )
      .distinct
      .toVector

  def toDependencyText(module: ModuleID, platform: DetectedPlatform, sbtVersion: String, scalaVersion: String): String =
    Vector(
      module.organization,
      toArtifactName(module = module, platform = platform, sbtVersion = sbtVersion, scalaVersion = scalaVersion),
      module.revision,
      toCrossVersionToken(module),
      toScopeToken(module),
      resolvesOnPlatform(module = module, platform = platform, sbtVersion = sbtVersion).toString
    ).mkString(dependencyFieldSeparator)

  private def resolvesOnPlatform(module: ModuleID, platform: DetectedPlatform, sbtVersion: String): Boolean =
    toPlatformPrefix(module).nonEmpty ||
      (SbtVersionRules.appliesPlatformPerProject(sbtVersion) &&
        platform =!= DetectedPlatform.Jvm &&
        crossesScalaVersion(module) &&
        !isCompilerPlugin(module))

  private def toPlatformPrefix(module: ModuleID): String = module.crossVersion match {
    case binary: CrossVersion.Binary => binary.prefix
    case full: CrossVersion.Full     => full.prefix
    case _                           => ""
  }

  private def crossesScalaVersion(module: ModuleID): Boolean = module.crossVersion match {
    case _: CrossVersion.Binary | _: CrossVersion.Full => true
    case _                                             => false
  }

  private def isCompilerPlugin(module: ModuleID): Boolean =
    module.configurations.exists(configuration => configuration.startsWith("plugin->"))

  private def toArtifactName(
      module: ModuleID,
      platform: DetectedPlatform,
      sbtVersion: String,
      scalaVersion: String
  ): String =
    if (crossesScalaVersion(module)) module.name
    else
      CrossVersion(
        cross = module.crossVersion,
        fullVersion = scalaVersion,
        binaryVersion = CrossVersion.binaryScalaVersion(scalaVersion)
      ).fold(module.name)(appendScalaSuffix =>
        appendScalaSuffix(appendPlatformSuffix(module = module, platform = platform, sbtVersion = sbtVersion))
      )

  private def appendPlatformSuffix(module: ModuleID, platform: DetectedPlatform, sbtVersion: String): String =
    if (SbtVersionRules.appliesPlatformPerProject(sbtVersion) && !isCompilerPlugin(module))
      toPlatformToken(platform).fold(module.name)(token => s"${module.name}_$token")
    else module.name

  private def toPlatformToken(platform: DetectedPlatform): Option[String] = platform match {
    case DetectedPlatform.Jvm                  => None
    case DetectedPlatform.ScalaJs(version)     => Some(PlatformToken.renderScalaJsToken(version))
    case DetectedPlatform.ScalaNative(version) => Some(PlatformToken.renderScalaNativeToken(version))
  }

  private def toCrossVersionToken(module: ModuleID): String = module.crossVersion match {
    case _: CrossVersion.Binary => binaryCrossVersion
    case _: CrossVersion.Full   => fullCrossVersion
    case _                      => disabledCrossVersion
  }

  private def toScopeToken(module: ModuleID): String =
    if (isCompilerPlugin(module)) pluginScope
    else
      module.configurations match {
        case Some("provided") | Some("optional") => providedScope
        case _                                   => compileScope
      }

  private def isSlicedConfiguration(module: ModuleID): Boolean = module.configurations match {
    case Some(configuration) =>
      isCompilerPlugin(module) ||
      configuration === "compile" ||
      configuration === "provided" ||
      configuration === "optional"
    case None => true
  }

  def findProjectsMissingSemanticdb(projects: Seq[(String, Boolean)]): Option[String] = {
    val without = projects.collect { case (id, false) => id }

    if (without.isEmpty) None
    else Some(s"slice reads SemanticDB; these projects have it off: ${without.mkString(", ")}")
  }
}
