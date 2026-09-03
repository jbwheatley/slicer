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

package slicer.model

import cats.Eq
import cats.syntax.eq.*

sealed trait Platform {

  def token: String

  final def artifactSuffix: String = if (token.isEmpty) "" else "_" + token

  final def crossPrefix: String = if (token.isEmpty) "" else token + "_"
}

object Platform {

  case object Jvm extends Platform {
    override val token: String = ""
  }

  final case class ScalaJs(version: String) extends Platform {
    override def token: String = "sjs" + toBinaryVersion(version)
  }

  final case class ScalaNative(version: String) extends Platform {
    override def token: String = "native" + toBinaryVersion(version)
  }

  def toBinaryVersion(version: String): String =
    version.split('.').toVector match {
      case major +: _ if major =!= "0" => major
      case major +: minor +: _         => s"$major.$minor"
      case parts                       => parts.mkString(".")
    }

  given Eq[Platform] = Eq.fromUniversalEquals
}
