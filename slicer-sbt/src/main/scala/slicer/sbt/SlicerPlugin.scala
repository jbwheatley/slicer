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

import slicer.analysis.ScalaVersionRules
import slicer.emit.WrittenSlices
import slicer.model.SliceOptions
import slicer.tui.SliceInputs

import _root_.sbt.Keys.*
import _root_.sbt.{Def, *}

object SlicerPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {

    val sliceClear: TaskKey[Unit] =
      taskKey[Unit]("Remove every slice this project's picker has written under target/slice.")
  }

  import autoImport.*

  private val sliceInputs: TaskKey[SliceInputs] = taskKey[SliceInputs]("")

  private def sliceOut: Def.Initialize[Path] = Def.setting((target.value / "slice").toPath)

  override def projectSettings: Seq[Setting[?]] = Seq(
    semanticdbEnabled := true,
    semanticdbOptions ++= ScalaVersionRules.rulesForScalaVersion(scalaVersion.value).semanticdbOptions,
    sliceClear := Def.uncached {
      val out = sliceOut.value
      val log = streams.value.log
      WrittenSlices.clearWrittenSlices(out) match {
        case Left(error)     => throw error // scalafix:ok DisableSyntax.throw
        case Right(messages) => messages.foreach(log.info(_))
      }
    },
    sliceInputs := Def.uncached { buildSliceInputsTask.value }
  )

  override def globalSettings: Seq[Setting[?]] = Seq(commands += slicePicker)

  private def slicePicker: Command = Command.args("slice", "<symbol>") { (state, args) =>
    val extracted = Project.extract(state)
    val (next, inputs) = extracted.runTask(extracted.currentRef / sliceInputs, state)
    SbtSlicePicker.openPicker(inputs, query = args.mkString(" ").trim, options = SliceOptions.default) match {
      case Left(error) => throw error // scalafix:ok DisableSyntax.throw
      case Right(_)    => next
    }
  }

  private val sliceScope = ScopeFilter(
    inDependencies(ThisProject, transitive = true, includeRoot = true) ||
      inAggregates(ThisProject, transitive = true, includeRoot = true)
  )

  private def buildSliceInputsTask: Def.Initialize[Task[SliceInputs]] = Def.task {
    Def.uncached {
      val projects = thisProject.all(sliceScope).value.map(_.id).zip(semanticdbEnabled.all(sliceScope).value)
      SbtSliceInputs.findProjectsMissingSemanticdb(projects).foreach(error => sys.error(error))

      val _ = (Compile / compile).all(sliceScope).value
      val modules = libraryDependencies.all(sliceScope).value.flatten
      val platform = SbtSliceInputs.detectPlatform(modules)
      SbtSliceInputs.buildSliceInputs(
        sourceRoot = (ThisBuild / baseDirectory).value.toPath,
        semanticdbDirs = (Compile / semanticdbTargetRoot).all(sliceScope).value.map(_.toPath).toVector,
        sourceDirs = ((Compile / unmanagedSourceDirectories).all(sliceScope).value.flatten ++
          (Compile / managedSourceDirectories).all(sliceScope).value.flatten).map(_.toPath).toVector,
        out = sliceOut.value,
        scalaVersion = scalaVersion.value,
        sbtVersion = (pluginCrossBuild / sbtVersion).value,
        dependencies = SbtSliceInputs.collectDependencies(modules, platform),
        scalacOptions = (Compile / scalacOptions).all(sliceScope).value.flatten.toVector,
        platform = platform
      ) match {
        case Left(error)   => throw error // scalafix:ok DisableSyntax.throw
        case Right(inputs) => inputs
      }
    }
  }

}
