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

import java.util.regex.Pattern

import cats.syntax.eq.*

private[slicer] final case class Dependency(
    organization: String,
    artifact: String,
    version: String,
    crossVersion: CrossVersion,
    scope: DependencyScope,
    platformed: Boolean
) {

  private def plainCoordinate: String = s""""$organization" % "$artifact" % "$version""""

  def renderSbtSyntax(platform: Platform, platformAppliedByBuild: Boolean): String = {
    val binary = s""""$organization" %% "$artifact" % "$version""""
    val full = s"""($plainCoordinate).cross(CrossVersion.full)"""

    val module =
      if (!platformed || platformAppliedByBuild)
        crossVersion match {
          case CrossVersion.Disabled if platformed =>
            s"""($plainCoordinate).cross(CrossVersion.constant("${platform.token}"))"""
          case CrossVersion.Disabled => plainCoordinate
          case CrossVersion.Binary   => binary
          case CrossVersion.Full     => full
        }
      else
        crossVersion match {
          case CrossVersion.Disabled => s"""($plainCoordinate).cross(CrossVersion.constant("${platform.token}"))"""
          case CrossVersion.Binary =>
            s"""($plainCoordinate).cross(CrossVersion.binaryWith("${platform.crossPrefix}", ""))"""
          case CrossVersion.Full =>
            s"""($plainCoordinate).cross(CrossVersion.fullWith("${platform.crossPrefix}", ""))"""
        }

    scope match {
      case DependencyScope.Compile  => module
      case DependencyScope.Provided => s"$module % Provided"
      case DependencyScope.Plugin   => s"compilerPlugin($module)"
    }
  }

  def renderMillSyntax: String = {
    val separator = crossVersion match {
      case CrossVersion.Disabled => ":"
      case CrossVersion.Binary   => "::"
      case CrossVersion.Full     => ":::"
    }
    val beforeVersion = if (platformed) "::" else ":"
    s"""mvn"$organization$separator$artifact$beforeVersion$version""""
  }

  def toCoordinate(scalaVersion: String, platform: Platform): String = {
    val suffix = if (platformed) platform.artifactSuffix else ""
    s"$organization:$artifact$suffix${crossVersion.toArtifactSuffix(scalaVersion)}:$version"
  }

  def renderAsText: String =
    Vector(organization, artifact, version, crossVersion.toString, scope.toString, platformed.toString)
      .mkString(Dependency.fieldSeparator)
}

private[slicer] object Dependency {

  private val fieldSeparator: String = "|"

  def parse(text: String): Either[SliceFailure, Dependency] =
    text.split(Pattern.quote(fieldSeparator), -1).toVector match {
      case Vector(organization, artifact, version, crossVersion, scope, platformed) =>
        for {
          cross <- findTokenType(all = CrossVersion.values.toVector, token = crossVersion, desc = "cross-version")
          declared <- findTokenType(all = DependencyScope.values.toVector, token = scope, desc = "scope")
          resolves <- platformed.toBooleanOption.toRight(
            SliceFailure(s"dependency has an unreadable platform flag: $platformed")
          )
        } yield Dependency(
          organization = organization,
          artifact = artifact,
          version = version,
          crossVersion = cross,
          scope = declared,
          platformed = resolves
        )
      case fields => Left(SliceFailure(s"dependency has ${fields.size} fields rather than 6: $text"))
    }

  private def findTokenType[A](all: Vector[A], token: String, desc: String): Either[SliceFailure, A] =
    all.find(_.toString === token).toRight(SliceFailure(s"dependency has an unknown $desc: $token"))

  def sortDependencies(dependencies: Seq[Dependency]): Vector[Dependency] =
    dependencies.distinct
      .sortBy(dependency => (dependency.organization, dependency.artifact, dependency.version))
      .toVector

  def filterToScope(dependencies: Vector[Dependency], scope: DependencyScope): Vector[Dependency] =
    dependencies.filter(_.scope === scope)
}
