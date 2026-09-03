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

import java.net.URLClassLoader
import java.nio.file.{Path, Paths}

import scala.util.Try

private[slicer] object PickerClasspath {

  def readPluginClasspath(loaded: Class[?]): Either[String, Vector[Path]] = loaded.getClassLoader match {
    case urls: URLClassLoader =>
      val entries = urls.getURLs.toVector.flatMap(url => Try(Paths.get(url.toURI)).toOption)
      if (entries.isEmpty) Left(s"slice could not read a classpath from the loader of ${loaded.getName}")
      else Right(entries)
    case other =>
      val loader = Option(other) match {
        case Some(present) => present.getClass.getName
        case None          => "the runtime's own loader"
      }
      Left(
        "slice runs its picker in its own process and needs the plugin's classpath, " +
          s"which $loader does not expose"
      )
  }
}
