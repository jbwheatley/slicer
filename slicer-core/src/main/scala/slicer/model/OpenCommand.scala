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

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.util.Try

private[slicer] final case class OpenCommand(editor: String, command: String)

private[slicer] object OpenCommand {
  def openCommandsForDirectory(directory: Path): Vector[OpenCommand] = {
    val target = quote(directory.toAbsolutePath.normalize().toString)
    val editors = intellijCommands(target) ++ visualStudioCodeCommands(target)
    if (editors.nonEmpty) editors else fileManagerCommand(target).toVector
  }

  private def intellijCommands(target: String): Vector[OpenCommand] = {
    val launcher = findFirstOnPath(Vector("idea", "idea-ce", "idea.bat", "idea.cmd"))
      .orElse(findFirstExecutable(toolboxScripts.map(_.resolve("idea"))))
      .map(command => s"$command $target")
    val application = findFirstExisting(macApplications("IntelliJ IDEA", "IntelliJ IDEA Community Edition"))
      .map(app => s"""open -na ${quote(app.toString)} --args $target""")
    launcher.orElse(application).map(command => OpenCommand("IntelliJ", command)).toVector
  }

  private def visualStudioCodeCommands(target: String): Vector[OpenCommand] = {
    val launcher = findFirstOnPath(Vector("code", "code.cmd", "codium")).map(command => s"$command $target")
    val application = findFirstExisting(macApplications("Visual Studio Code"))
      .map(app => s"""open -na ${quote(app.toString)} --args $target""")
    launcher.orElse(application).map(command => OpenCommand("VS Code", command)).toVector
  }

  private def fileManagerCommand(target: String): Option[OpenCommand] = {
    val command =
      if (isMac) Some(s"open $target")
      else findFirstOnPath(Vector("xdg-open", "explorer.exe")).map(launcher => s"$launcher $target")
    command.map(line => OpenCommand("Files", line))
  }

  private def toolboxScripts: Vector[Path] = {
    val home = Paths.get(sys.props.getOrElse("user.home", "."))
    Vector(
      home.resolve("Library/Application Support/JetBrains/Toolbox/scripts"),
      home.resolve(".local/share/JetBrains/Toolbox/scripts")
    )
  }

  private def macApplications(names: String*): Vector[Path] = {
    val roots = Vector(Paths.get("/Applications"), Paths.get(sys.props.getOrElse("user.home", "."), "Applications"))
    if (isMac) roots.flatMap(root => names.map(name => root.resolve(s"$name.app"))) else Vector.empty
  }

  private def isMac: Boolean = sys.props.getOrElse("os.name", "").toLowerCase.contains("mac")

  private def findFirstOnPath(commands: Vector[String]): Option[String] =
    commands.find(command => pathEntries.exists(entry => Files.isExecutable(entry.resolve(command))))

  private def pathEntries: Vector[Path] =
    sys.env
      .getOrElse("PATH", "")
      .split(File.pathSeparator)
      .toVector
      .filter(_.nonEmpty)
      .flatMap(entry => Try(Paths.get(entry)).toOption)

  private def findFirstExecutable(candidates: Vector[Path]): Option[String] =
    candidates.find(Files.isExecutable).map(_.toString).map(quote)

  private def findFirstExisting(candidates: Vector[Path]): Option[Path] = candidates.find(Files.exists(_))

  private def quote(path: String): String = if (path.contains(" ")) s"'$path'" else path
}
