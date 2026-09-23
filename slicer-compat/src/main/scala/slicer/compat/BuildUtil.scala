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

private[slicer] object BuildUtil {

  val semanticdbSynthetics: String = "-P:semanticdb:synthetics:on"

  def semanticdbOptionsForScalaVersion(version: String): Vector[String] =
    if (version.startsWith("2.13")) Vector(semanticdbSynthetics) else Vector.empty

  def appliesPlatformPerProject(sbtVersion: String): Boolean = !sbtVersion.startsWith("1.")

  def renderScalaJsToken(version: String): String = "sjs" + toPlatformBinaryVersion(version)

  def renderScalaNativeToken(version: String): String = "native" + toPlatformBinaryVersion(version)

  def toPlatformBinaryVersion(version: String): String =
    version.split('.').toVector match {
      case "0" +: minor +: _ => s"0.$minor"
      case major +: _        => major
      case parts             => parts.mkString(".")
    }
}
