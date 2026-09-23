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

package slicer.sbt

import cats.Eq

private[slicer] sealed trait DetectedPlatform

private[slicer] object DetectedPlatform {

  case object Jvm extends DetectedPlatform

  final case class ScalaJs(version: String) extends DetectedPlatform

  final case class ScalaNative(version: String) extends DetectedPlatform

  implicit val eq: Eq[DetectedPlatform] = Eq.fromUniversalEquals
}
