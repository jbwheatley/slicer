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

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

private[slicer] object Directories {

  def listChildDirectories(directory: Path): Vector[Path] =
    if (!Files.isDirectory(directory)) Vector.empty
    else {
      val children = Files.list(directory)
      try children.toArray(size => new Array[Path](size)).toVector.filter(Files.isDirectory(_)).sortBy(_.toString)
      finally children.close()
    }

  private val deleteWhileWalking: SimpleFileVisitor[Path] = new SimpleFileVisitor[Path] {

    override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
      val _ = Files.deleteIfExists(file)
      FileVisitResult.CONTINUE
    }

    override def visitFileFailed(file: Path, failure: IOException): FileVisitResult = FileVisitResult.CONTINUE

    override def postVisitDirectory(directory: Path, failure: IOException): FileVisitResult = {
      val _ = Files.deleteIfExists(directory)
      FileVisitResult.CONTINUE
    }
  }

  def deleteRecursively(directory: Path): Unit =
    if (Files.exists(directory)) {
      val _ = Files.walkFileTree(directory, deleteWhileWalking)
    }
}
