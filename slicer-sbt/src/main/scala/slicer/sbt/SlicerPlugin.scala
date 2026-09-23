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

import java.nio.file.Path

import slicer.compat.{SemanticdbOptions, WrittenSlices}

import sbt.Keys.*
import sbt.internal.util.MessageOnlyException
import sbt.{Def, *}

object SlicerPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {

    val sliceClear: TaskKey[Unit] =
      taskKey[Unit]("Remove every slice this project's picker has written under target/slice.")
  }

  private[slicer] val sliceArguments: TaskKey[Vector[String]] = taskKey[Vector[String]]("")

  private def sliceOut: Def.Initialize[Path] = Def.setting((target.value / "slice").toPath)

  override def projectSettings: Seq[Setting[?]] = OpenSlicePicker.settings ++ Seq(
    semanticdbEnabled := true,
    semanticdbOptions ++= SemanticdbOptions.optionsForScalaVersion(scalaVersion.value)
  )

  override def globalSettings: Seq[Setting[?]] = Seq(commands += openSlicePicker)

  private def openSlicePicker: Command = Command.args(name = "slice", display = "<symbol>") { (state, args) =>
    OpenSlicePicker.openPicker(state, args)
  }

  private[sbt] def runSliceArguments(state: State, query: Seq[String]): (State, Vector[String]) = {
    val extracted = Project.extract(state)
    val (next, arguments) = extracted.runTask(extracted.currentRef / sliceArguments, state)

    (next, arguments :+ SbtSliceRequest.renderQuery(query.mkString(" ").trim))
  }

  private val sliceScope = ScopeFilter(
    projects = inDependencies(ref = ThisProject, transitive = true, includeRoot = true) ||
      inAggregates(ref = ThisProject, transitive = true, includeRoot = true)
  )

  private[slicer] def clearSlicesTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    WrittenSlices.clearWrittenSlices(sliceOut.value) match {
      case Left(error)     => throw new MessageOnlyException(error) // scalafix:ok DisableSyntax.throw
      case Right(messages) => messages.foreach(message => log.info(message))
    }
  }

  private[slicer] def buildSliceArgumentsTask: Def.Initialize[Task[Vector[String]]] = Def.task {
    val projects = thisProject.all(sliceScope).value.map(_.id).zip(semanticdbEnabled.all(sliceScope).value)
    SbtSliceRequest
      .findProjectsMissingSemanticdb(projects)
      .foreach(error => throw new MessageOnlyException(error)) // scalafix:ok DisableSyntax.throw

    val _ = (Compile / compile).all(sliceScope).value

    SbtSliceRequest.renderAsArgs(
      sourceRoot = (ThisBuild / baseDirectory).value.toPath,
      out = sliceOut.value,
      semanticdbDirs = (Compile / semanticdbTargetRoot).all(sliceScope).value.map(_.toPath).toVector,
      sourceDirs = ((Compile / unmanagedSourceDirectories).all(sliceScope).value.flatten ++
        (Compile / managedSourceDirectories).all(sliceScope).value.flatten).map(_.toPath).toVector,
      scalaVersion = scalaVersion.value,
      sbtVersion = (pluginCrossBuild / sbtVersion).value,
      modules = libraryDependencies.all(sliceScope).value.flatten,
      scalacOptions = (Compile / scalacOptions).all(sliceScope).value.flatten.toVector
    )
  }
}
