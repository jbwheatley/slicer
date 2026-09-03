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

package slicer.util

import java.nio.file.{Files as JFiles, Path}
import java.util.Comparator

import scala.jdk.CollectionConverters.*
import scala.util.Using

private[slicer] object Files {

  def listChildDirectories(directory: Path): Vector[Path] =
    if (!JFiles.isDirectory(directory)) Vector.empty
    else
      Using.resource(JFiles.list(directory))(
        _.iterator().asScala.filter(JFiles.isDirectory(_)).toVector.sortBy(_.toString)
      )

  def deleteRecursively(directory: Path): Unit =
    if (JFiles.exists(directory))
      Using.resource(JFiles.walk(directory))(
        _.sorted(Comparator.reverseOrder[Path]).forEach(path => JFiles.deleteIfExists(path): Unit)
      )
}
