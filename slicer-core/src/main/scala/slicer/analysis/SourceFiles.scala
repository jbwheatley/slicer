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

package slicer.analysis

import java.nio.file.{Files, Path}

import scala.util.Try

private[analysis] object SourceFiles {

  def listSourceFilesUnder(directory: Path): Vector[Path] = listSourceFilesUnder(directory, Set.empty)

  private def listSourceFilesUnder(directory: Path, visitedDirectories: Set[Path]): Vector[Path] =
    if (Files.isDirectory(directory)) {
      val here = resolveRealPath(directory)
      if (visitedDirectories.contains(here)) Vector.empty
      else
        Option(directory.toFile.listFiles()).toVector.flatten
          .flatMap(child => listSourceFilesUnder(child.toPath, visitedDirectories + here))
    } else if (Files.exists(directory)) Vector(directory)
    else Vector.empty

  private def resolveRealPath(directory: Path): Path =
    Try(directory.toRealPath()).getOrElse(directory.toAbsolutePath.normalize())
}
