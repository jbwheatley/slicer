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

import slicer.analysis.ScalaVersionRules
import slicer.emit.WrittenSlices
import slicer.model.{BuildTool, DependencyScope, Platform, SliceOptions}

import mill.*
import mill.api.JsonFormatters.given
import mill.api.Task
import mill.scalajslib.ScalaJSModule
import mill.scalalib.ScalaModule
import mill.scalanativelib.ScalaNativeModule
import mill.util.Jvm

trait SlicerModule extends ScalaModule {

  def sliceOptions: SliceOptions = SliceOptions.default

  override protected def semanticDbEnablePluginScalacOptions: T[Seq[String]] = Task {
    super.semanticDbEnablePluginScalacOptions() ++
      ScalaVersionRules.rulesForScalaVersion(scalaVersion()).semanticdbOptions
  }

  private def sliceModules: Seq[ScalaModule] = this +: recursiveModuleDeps.collect { case module: ScalaModule =>
    module
  }

  def slice(query: String*): Command[Unit] = Task.Command(exclusive = true) {
    val arguments = sliceArguments(query.mkString(" ").trim)()

    PickerClasspath.readPluginClasspath(classOf[SlicerModule]) match {
      case Left(error) => Task.fail(error)
      case Right(classPath) =>
        Jvm.callInteractiveProcess(
          mainClass = MillSlicePicker.mainClass,
          classPath = classPath.map(os.Path(_)),
          mainArgs = arguments,
          cwd = mill.api.BuildCtx.workspaceRoot
        ) match {
          case 0    => ()
          case code => Task.fail(s"picker exited with $code")
        }
    }
  }

  def sliceClear(): Command[Unit] = Task.Command {
    val out = sliceDestination().toNIO
    WrittenSlices.clearWrittenSlices(out) match {
      case Left(error)     => Task.fail(error.getMessage + error.cause.fold("")(th => s": ${th.getMessage}"))
      case Right(messages) => messages.foreach(Task.log.info(_))
    }
  }

  def sliceDestination: T[os.Path] = Task { Task.dest }

  def slicePlatform(): Task[Platform] = this match {
    case js: ScalaJSModule         => Task.Anon { Platform.ScalaJs(js.scalaJSVersion()) }
    case native: ScalaNativeModule => Task.Anon { Platform.ScalaNative(native.scalaNativeVersion()) }
    case _                         => Task.Anon { Platform.Jvm }
  }

  def sliceArguments(query: String): Task[Vector[String]] = Task.Anon {
    SliceArguments.renderAsArgs(
      sourceRoot = mill.api.BuildCtx.workspaceRoot.toNIO,
      out = sliceDestination().toNIO,
      semanticdbDirs = Task.traverse(sliceModules)(_.semanticDbData)().map(data => data.path.toNIO).toVector,
      sourceDirs = Task.traverse(sliceModules)(_.allSources)().flatten.map(source => source.path.toNIO).toVector,
      tool = BuildTool.Mill(
        scalaVersion = scalaVersion(),
        millVersion = mill.api.BuildInfo.millVersion,
        dependencies = MillSliceInputs
          .collectDependencies(Task.traverse(sliceModules)(_.mvnDeps)().flatten, DependencyScope.Compile) ++
          MillSliceInputs.collectDependencies(
            Task.traverse(sliceModules)(_.compileMvnDeps)().flatten,
            DependencyScope.Provided
          ) ++
          MillSliceInputs.collectDependencies(
            Task.traverse(sliceModules)(_.scalacPluginMvnDeps)().flatten,
            DependencyScope.Plugin
          ),
        scalacOptions = Task.traverse(sliceModules)(_.scalacOptions)().flatten.toVector,
        platform = slicePlatform()()
      ),
      query = query,
      options = sliceOptions
    )
  }

}
