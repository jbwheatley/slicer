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

import java.nio.file.{Path, Paths}

import slicer.model.*

import cats.syntax.either.*
import cats.syntax.traverse.*

private[slicer] object SliceArguments {

  type Fields = Vector[(String, String)]

  private val sourceRootKey = "source-root"
  private val outKey = "out"
  private val semanticdbKey = "semanticdb"
  private val sourceDirKey = "source-dir"
  private val scalaVersionKey = "scala-version"
  private val millVersionKey = "mill-version"
  private val dependencyKey = "dependency"
  private val scalacOptionKey = "scalac-option"
  private val platformKey = "platform"
  private val platformVersionKey = "platform-version"
  private val queryKey = "query"
  private val followImplementationsKey = "follow-implementations"
  private val keepFieldsKey = "keep-fields"

  private val jvmPlatform = "jvm"
  private val scalaJsPlatform = "scala-js"
  private val scalaNativePlatform = "scala-native"

  def renderAsArgs(
      sourceRoot: Path,
      out: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      tool: BuildTool.Mill,
      query: String,
      options: SliceOptions
  ): Vector[String] =
    Vector(
      s"$sourceRootKey=$sourceRoot",
      s"$outKey=$out",
      s"$scalaVersionKey=${tool.scalaVersion}",
      s"$millVersionKey=${tool.millVersion}",
      s"$platformKey=${toPlatformName(tool.platform)}",
      s"$platformVersionKey=${toPlatformVersion(tool.platform)}",
      s"$queryKey=$query",
      s"$followImplementationsKey=${options.followImplementations}",
      s"$keepFieldsKey=${options.keepFields}"
    ) ++
      semanticdbDirs.map(directory => s"$semanticdbKey=$directory") ++
      sourceDirs.map(directory => s"$sourceDirKey=$directory") ++
      tool.dependencies.map(dependency => s"$dependencyKey=${dependency.renderAsText}") ++
      tool.scalacOptions.map(option => s"$scalacOptionKey=$option")

  def toFields(args: Vector[String]): Either[SliceFailure, Fields] = {
    val arguments = args.filter(_.nonEmpty)
    val malformed = arguments.filterNot(_.contains('='))

    if (malformed.nonEmpty)
      Left(SliceFailure(s"slice request has arguments without a key: ${malformed.mkString(", ")}"))
    else
      Right(arguments.map { argument =>
        val separator = argument.indexOf('=')
        (argument.take(separator), argument.drop(separator + 1))
      })
  }

  def readSourceRoot(fields: Fields): Either[SliceFailure, Path] = readPath(fields, sourceRootKey)

  def readOut(fields: Fields): Either[SliceFailure, Path] = readPath(fields, outKey)

  def readSemanticdbDirs(fields: Fields): Either[SliceFailure, Vector[Path]] = readPaths(fields, semanticdbKey)

  def readSourceDirs(fields: Fields): Either[SliceFailure, Vector[Path]] = readPaths(fields, sourceDirKey)

  def readQuery(fields: Fields): String = fields.collectFirst { case (`queryKey`, value) => value }.getOrElse("")

  def readBuildTool(fields: Fields): Either[SliceFailure, BuildTool.Mill] =
    for {
      scalaVersion <- readValue(fields, scalaVersionKey)
      millVersion <- readValue(fields, millVersionKey)
      dependencies <- readValues(fields, dependencyKey).traverse(Dependency.parse)
    } yield BuildTool.Mill(
      scalaVersion = scalaVersion,
      millVersion = millVersion,
      dependencies = dependencies,
      scalacOptions = readValues(fields, scalacOptionKey),
      platform = readPlatform(fields)
    )

  def readOptions(fields: Fields): Either[SliceFailure, SliceOptions] =
    for {
      followImplementations <- readFlag(fields = fields, key = followImplementationsKey, whenAbsent = true)
      keepFields <- readFlag(fields = fields, key = keepFieldsKey, whenAbsent = false)
    } yield SliceOptions(followImplementations = followImplementations, keepFields = keepFields)

  private def toPlatformName(platform: Platform): String = platform match {
    case Platform.Jvm            => jvmPlatform
    case _: Platform.ScalaJs     => scalaJsPlatform
    case _: Platform.ScalaNative => scalaNativePlatform
  }

  private def toPlatformVersion(platform: Platform): String = platform match {
    case Platform.Jvm                  => ""
    case Platform.ScalaJs(version)     => version
    case Platform.ScalaNative(version) => version
  }

  private def readPlatform(fields: Fields): Platform = {
    val version = readValues(fields, platformVersionKey).headOption.getOrElse("")
    readValues(fields, platformKey).headOption match {
      case Some(`scalaJsPlatform`)     => Platform.ScalaJs(version)
      case Some(`scalaNativePlatform`) => Platform.ScalaNative(version)
      case _                           => Platform.Jvm
    }
  }

  private def readPath(fields: Fields, key: String): Either[SliceFailure, Path] =
    readValue(fields, key).flatMap(toPath)

  private def readPaths(fields: Fields, key: String): Either[SliceFailure, Vector[Path]] =
    readValues(fields, key).traverse(toPath)

  private def toPath(value: String): Either[SliceFailure, Path] =
    Either.catchNonFatal(Paths.get(value)).leftMap { th =>
      SliceFailure(s"slice request carries an unusable path $value", th)
    }

  private def readValue(fields: Fields, key: String): Either[SliceFailure, String] =
    readValues(fields, key).headOption.toRight(SliceFailure(s"slice request carries no $key"))

  private def readValues(fields: Fields, key: String): Vector[String] =
    fields.collect { case (`key`, value) if value.nonEmpty => value }

  private def readFlag(fields: Fields, key: String, whenAbsent: Boolean): Either[SliceFailure, Boolean] =
    fields.collectFirst { case (`key`, value) if value.nonEmpty => value } match {
      case Some(value) => value.toBooleanOption.toRight(SliceFailure(s"slice request has an unreadable $key: $value"))
      case None        => Right(whenAbsent)
    }
}
