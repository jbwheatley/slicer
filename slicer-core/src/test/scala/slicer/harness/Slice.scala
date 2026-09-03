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

package slicer.harness

import java.nio.file.{Files, Path}

import slicer.model.{DefNode, SliceResult}

final case class Slice(root: DefNode, result: SliceResult, files: Map[String, String], compiledFirst: Set[String]) {

  lazy val text: String = files.toSeq.sortBy(_._1).map(_._2).mkString("\n")

  def file(suffix: String): String =
    files
      .collectFirst { case (name, body) if name.endsWith(suffix) => body }
      .getOrElse(
        sys.error(s"slice of ${root.dottedName} has no file ending in '$suffix'; it has ${files.keys.mkString(", ")}")
      )

  def writeTo(directory: Path): Path = {
    files.foreach { case (name, body) =>
      val target = directory.resolve(name)
      Files.createDirectories(target.getParent)
      Files.writeString(target, body)
    }
    directory
  }
}
