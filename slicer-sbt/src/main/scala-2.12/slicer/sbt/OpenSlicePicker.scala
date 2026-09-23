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

import java.io.File
import java.nio.file.{Path, Paths}

import slicer.compat.ArgumentUtil

import sbt.*
import sbt.Keys.*
import sbt.internal.util.MessageOnlyException
import sbt.librarymanagement.{DependencyResolution, UnresolvedWarningConfiguration, UpdateConfiguration}

private[slicer] object OpenSlicePicker {

  private val pickerJavaVersion: Int = 17

  private def resolvePickerClasspath(resolution: DependencyResolution, log: Logger): Vector[Path] =
    resolution.update(
      module = resolution.wrapDependencyInModule(
        ("io.github.jbwheatley" % "slicer-tui_3" % SlicerVersion.version).exclude("org.scala-lang", "scalap")
      ),
      configuration = UpdateConfiguration(),
      uwconfig = UnresolvedWarningConfiguration(),
      log = log
    ) match {
      case Left(unresolved) => failWith(unresolved.resolveException.getMessage)
      case Right(report)    => report.allFiles.distinct.map(jar => jar.toPath)
    }

  def openPicker(state: State, query: Seq[String]): State = {
    findJavaTooOldForPicker(sys.props.getOrElse("java.specification.version", "")).foreach(failWith)

    val (withArguments, arguments) = SlicerPlugin.runSliceArguments(state, query)
    val extracted = Project.extract(withArguments)
    val (next, resolution) = extracted.runTask(extracted.currentRef / dependencyResolution, withArguments)

    forkPicker(
      classPath = resolvePickerClasspath(resolution = resolution, log = next.log),
      arguments = arguments,
      cwd = extracted.get(extracted.currentRef / baseDirectory).toPath
    )

    next
  }

  private[sbt] def findJavaTooOldForPicker(specificationVersion: String): Option[String] = {
    val feature = specificationVersion.stripPrefix("1.").takeWhile(_.isDigit)

    if (feature.nonEmpty && feature.toInt < pickerJavaVersion)
      Some(
        s"slice opens its picker on Java $pickerJavaVersion or newer, and this sbt runs on Java $specificationVersion - " +
          "start sbt on a newer JDK, for example with sbt --java-home"
      )
    else None
  }

  private def forkPicker(classPath: Vector[Path], arguments: Vector[String], cwd: Path): Unit = {
    val java = Paths.get(sys.props.getOrElse("java.home", ""), "bin", "java").toString
    val command =
      Vector(java) ++ ArgumentUtil.pickerJvmOptions ++
        Vector("-cp", classPath.mkString(File.pathSeparator), ArgumentUtil.pickerMainClass) ++ arguments

    new ProcessBuilder(command*)
      .directory(cwd.toFile)
      .inheritIO()
      .start()
      .waitFor() match {
      case 0    => ()
      case code => failWith(s"picker exited with $code")
    }
  }

  private def failWith(message: String): Nothing =
    throw new MessageOnlyException(message) // scalafix:ok DisableSyntax.throw
}
