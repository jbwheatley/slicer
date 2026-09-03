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

import slicer.model.{CrossVersion as SliceCrossVersion, *}
import slicer.tui.SliceInputs

import _root_.sbt.librarymanagement.{CrossVersion, ModuleID}
import cats.syntax.eq.*

private[slicer] object SbtSliceInputs {

  def toDependency(module: ModuleID, platform: Platform): Dependency = {
    val crossVersion = module.crossVersion match {
      case _: CrossVersion.Binary => SliceCrossVersion.Binary
      case _: CrossVersion.Full   => SliceCrossVersion.Full
      case _                      => SliceCrossVersion.Disabled
    }

    Dependency(
      organization = module.organization,
      artifact = module.name,
      version = module.revision,
      crossVersion = crossVersion,
      scope = toDependencyScope(module),
      platformed = resolvesOnPlatform(module = module, crossVersion = crossVersion, platform = platform)
    )
  }

  private def resolvesOnPlatform(
      module: ModuleID,
      crossVersion: SliceCrossVersion,
      platform: Platform
  ): Boolean =
    toPlatformPrefix(module).nonEmpty ||
      (platform =!= Platform.Jvm && crossVersion =!= SliceCrossVersion.Disabled &&
        toDependencyScope(module) =!= DependencyScope.Plugin)

  private def toPlatformPrefix(module: ModuleID): String = module.crossVersion match {
    case binary: CrossVersion.Binary => binary.prefix
    case full: CrossVersion.Full     => full.prefix
    case _                           => ""
  }

  def detectPlatform(modules: Seq[ModuleID]): Platform = {
    val scalaJs = modules.collectFirst {
      case module if module.organization === "org.scala-js" && module.name.startsWith("scalajs-library") =>
        Platform.ScalaJs(module.revision)
    }
    val scalaNative = modules.collectFirst {
      case module if module.organization === "org.scala-native" && module.name.startsWith("nativelib") =>
        Platform.ScalaNative(module.revision)
    }
    scalaJs.orElse(scalaNative).getOrElse(Platform.Jvm)
  }

  def toDependencyScope(module: ModuleID): DependencyScope =
    module.configurations match {
      case Some(configuration) if configuration.startsWith("plugin->") => DependencyScope.Plugin
      case Some("provided") | Some("optional")                         => DependencyScope.Provided
      case _                                                           => DependencyScope.Compile
    }

  def collectDependencies(modules: Seq[ModuleID], platform: Platform): Vector[Dependency] =
    Dependency.sortDependencies(
      modules.filter(module => isSlicedConfiguration(module)).map(module => toDependency(module, platform))
    )

  private def isSlicedConfiguration(module: ModuleID): Boolean =
    module.configurations match {
      case Some(configuration) =>
        configuration.startsWith("plugin->") ||
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

  def buildSliceInputs(
      sourceRoot: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      out: Path,
      scalaVersion: String,
      sbtVersion: String,
      dependencies: Vector[Dependency],
      scalacOptions: Vector[String],
      platform: Platform
  ): Either[SliceFailure, SliceInputs] =
    SliceInputs.build(
      sourceRoot = sourceRoot,
      semanticdbDirs = semanticdbDirs,
      sourceDirs = sourceDirs,
      out = out,
      tool = BuildTool.Sbt(scalaVersion, sbtVersion, dependencies, scalacOptions, platform),
      compileFirstAdvice = "compile with semanticdbEnabled first"
    )
}
