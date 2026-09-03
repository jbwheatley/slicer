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

private[analysis] final class SourceLocator(sourceRoot: Path, sourceDirs: Vector[Path]) {

  private lazy val known: Vector[Path] =
    sourceDirs.distinct.flatMap(dir => SourceFiles.listSourceFilesUnder(dir).map(_.normalize()))

  def apply(uri: String): Option[Path] = {
    val direct = sourceRoot.resolve(uri).normalize()
    if (Files.exists(direct)) Some(direct)
    else {
      val suffix = uri.replace('\\', '/')
      known.filter(_.toString.replace('\\', '/').endsWith("/" + suffix)) match {
        case Vector(one) => Some(one)
        case Vector()    => None
        case many        => Some(many.minBy(_.toString.length))
      }
    }
  }
}
