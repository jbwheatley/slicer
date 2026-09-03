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

private[slicer] sealed trait BuildTool {
  def scalaVersion: String
  def name: String
  def dependencies: Vector[Dependency]
  def scalacOptions: Vector[String]
  def platform: Platform
}

private[slicer] object BuildTool {

  final case class Sbt(
      scalaVersion: String,
      sbtVersion: String,
      dependencies: Vector[Dependency],
      scalacOptions: Vector[String],
      platform: Platform
  ) extends BuildTool {
    override val name = "sbt"
  }

  final case class Mill(
      scalaVersion: String,
      millVersion: String,
      dependencies: Vector[Dependency],
      scalacOptions: Vector[String],
      platform: Platform
  ) extends BuildTool {
    override val name = "mill"
  }
}
