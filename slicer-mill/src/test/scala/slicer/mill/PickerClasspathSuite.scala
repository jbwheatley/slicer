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

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Path, Paths}

class PickerClasspathSuite extends munit.FunSuite {

  private val pluginClasspath: Vector[Path] =
    sys.props("slicer.pluginClasspath").split(File.pathSeparatorChar).toVector.map(entry => Paths.get(entry))

  test("the classpath the picker runs on is the one its own loader was given") {
    val loader = URLClassLoader(
      pluginClasspath.map(_.toUri.toURL).toArray,
      null // scalafix:ok DisableSyntax.null
    )
    val loaded = loader.loadClass("slicer.mill.PickerClasspath$")

    assertEquals(PickerClasspath.readPluginClasspath(loaded), Right(pluginClasspath))
  }

  test("a class the runtime itself loaded carries no classpath, and is reported rather than crashed on") {
    assert(PickerClasspath.readPluginClasspath(classOf[String]).isLeft)
  }
}
