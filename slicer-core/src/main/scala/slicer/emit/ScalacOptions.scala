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

private[slicer] object ScalacOptions {

  private val dropped: Vector[String] =
    Vector("-Xfatal-warnings", "-Werror", "-Xsemanticdb", "-Ysemanticdb", "-Yrangepos")

  private val droppedPrefixes: Vector[String] =
    Vector("-Wconf", "-P:semanticdb:", "-Xplugin:", "-Xplugin-require:", "-semanticdb-target:", "-sourceroot:")

  private val droppedWithValue: Vector[String] =
    Vector("-semanticdb-target", "-sourceroot", "-d", "-classpath", "-Xplugin")

  def filterForSlice(options: Vector[String]): Vector[String] =
    groupFlagsWithValues(dropUnwantedOptions(options)).distinct.flatten

  private def dropUnwantedOptions(options: Vector[String]): Vector[String] =
    options
      .foldLeft((kept = Vector.empty[String], skipping = false)) { (taken, option) =>
        if (taken.skipping) (kept = taken.kept, skipping = false)
        else if (droppedWithValue.contains(option)) (kept = taken.kept, skipping = true)
        else if (dropped.contains(option) || droppedPrefixes.exists(option.startsWith))
          (kept = taken.kept, skipping = false)
        else (kept = taken.kept :+ option, skipping = false)
      }
      .kept

  private def groupFlagsWithValues(options: Vector[String]): Vector[Vector[String]] =
    options.foldLeft(Vector.empty[Vector[String]]) { (grouped, option) =>
      grouped.lastOption match {
        case Some(flag) if !option.startsWith("-") => grouped.init :+ (flag :+ option)
        case _                                     => grouped :+ Vector(option)
      }
    }
}
