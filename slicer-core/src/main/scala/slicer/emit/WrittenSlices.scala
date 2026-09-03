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

import java.nio.file.Path

import slicer.model.SliceFailure
import slicer.util.ConsolePrint.*
import slicer.util.Files

import cats.syntax.either.*

private[slicer] object WrittenSlices {
  def clearWrittenSlices(out: Path): Either[SliceFailure, Vector[String]] = {
    Either
      .catchNonFatal(Files.listChildDirectories(out))
      .leftMap { th =>
        SliceFailure(s"could not list $out", th)
      }
      .flatMap { children =>
        val (errors, removed) = children
          .map { child =>
            Either
              .catchNonFatal(Files.deleteRecursively(child))
              .bimap(th => (th, child), _ => child)
          }
          .partitionMap(identity)
        if (errors.nonEmpty)
          Left(
            SliceFailure(
              s"failed to remove slices at ${errors.map(_._2).mkString(", ")}".toConsoleMessage,
              errors.head._1
            )
          )
        else Right(removed)
      }
      .map {
        case Vector() => Vector(s"no slices to remove under $out".toConsoleMessage)
        case removed  => removed.map(r => s"removed $r".toConsoleMessage)
      }
  }
}
