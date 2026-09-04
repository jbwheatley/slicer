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

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files as JFiles, Path, SimpleFileVisitor}

import scala.jdk.CollectionConverters.*
import scala.util.Using

private[slicer] object Files {

  def listChildDirectories(directory: Path): Vector[Path] =
    if (!JFiles.isDirectory(directory)) Vector.empty
    else
      Using.resource(JFiles.list(directory))(
        _.iterator().asScala.filter(JFiles.isDirectory(_)).toVector.sortBy(_.toString)
      )

  private val deleteWhileWalking: SimpleFileVisitor[Path] = new SimpleFileVisitor[Path] {

    override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
      JFiles.deleteIfExists(file): Unit
      FileVisitResult.CONTINUE
    }

    override def visitFileFailed(file: Path, failure: IOException): FileVisitResult = FileVisitResult.CONTINUE

    override def postVisitDirectory(directory: Path, failure: IOException): FileVisitResult = {
      JFiles.deleteIfExists(directory): Unit
      FileVisitResult.CONTINUE
    }
  }

  def deleteRecursively(directory: Path): Unit =
    if (JFiles.exists(directory)) JFiles.walkFileTree(directory, deleteWhileWalking): Unit
}
