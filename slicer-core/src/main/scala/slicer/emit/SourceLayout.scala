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

import scala.math.Ordering.Implicits.seqOrdering

import cats.syntax.eq.*

private[emit] object SourceLayout {

  val defaultSourceRootSegments: Vector[String] = Vector("src", "main", "scala")

  val defaultSourceRoot: String = defaultSourceRootSegments.mkString("/")

  private val languageDirectories: Vector[String] = Vector("scala", "java")

  private val generatedModule: String = "generated"

  def findSourceRoot(relative: java.nio.file.Path): Option[String] = {
    val parts = toPathSegments(relative)
    findSourceRootMarker(parts).map(marker => parts.take(marker + defaultSourceRootSegments.size).mkString("/"))
  }

  def findModuleTail(path: java.nio.file.Path): Option[java.nio.file.Path] = {
    val parts = toPathSegments(path)
    findSourceRootMarker(parts).map(marker =>
      java.nio.file.Paths.get(parts.drop(math.max(marker - 1, 0)).mkString("/"))
    )
  }

  def chooseCompiledRoots(roots: Vector[String]): Vector[String] =
    roots
      .groupBy(root => (toParentPath(root), toLanguageName(root)))
      .values
      .toVector
      .flatMap(keepNewestVariant)
      .distinct
      .sorted

  private def keepNewestVariant(variants: Vector[String]): Vector[String] = {
    val (plain, versioned) = variants.partition(variant => toVersionSuffix(variant).isEmpty)
    plain ++ versioned.maxByOption(variant => (toVersionOrder(variant), variant)).toVector
  }

  private def toVersionOrder(root: String): Vector[Int] =
    toVersionSuffix(root).split('.').toVector.flatMap(part => part.takeWhile(_.isDigit).toIntOption)

  private def toVersionSuffix(root: String): String = takeLastSegment(root).dropWhile(_ =!= '-').drop(1)

  private def toLanguageName(root: String): String = takeLastSegment(root).takeWhile(_ =!= '-')

  private def takeLastSegment(root: String): String = root.split('/').lastOption.getOrElse(root)

  private def toParentPath(root: String): String = root.split('/').dropRight(1).mkString("/")

  private def findSourceRootMarker(parts: Vector[String]): Option[Int] =
    parts.indices.find(marker =>
      parts.lift(marker).contains("src") &&
        parts.lift(marker + 1).contains("main") &&
        parts.lift(marker + 2).exists(isLanguageDirectory)
    )

  private def isLanguageDirectory(segment: String): Boolean =
    languageDirectories.contains(segment) || segment.startsWith("scala-")

  def toGeneratedSourcePath(relative: java.nio.file.Path): java.nio.file.Path = {
    val parts = toPathSegments(relative)
    val language = if (parts.lastOption.exists(_.endsWith(".java"))) "java" else "scala"
    val root = Vector(generatedModule, "src", "main", language)
    java.nio.file.Paths.get((root ++ toPackagedTail(parts)).mkString("/"))
  }

  private def toPackagedTail(parts: Vector[String]): Vector[String] = {
    val managed = parts.indexOf("src_managed")
    val dest = parts.indexWhere(_.endsWith(".dest"))
    if (managed >= 0) parts.drop(managed + 2)
    else if (dest >= 0) parts.drop(dest + 1)
    else parts.takeRight(1)
  }

  private def toPathSegments(relative: java.nio.file.Path): Vector[String] =
    (0 until relative.getNameCount).map(relative.getName(_).toString).toVector
}
