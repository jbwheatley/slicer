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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slicer.model.{BuildTool, Dependency, DependencyScope, Platform}

private[emit] object BuildFileWriter {

  private final case class MacroModule(directory: String, name: String, sourceRoots: Vector[String])

  private final case class Settings(
      scalaVersion: String,
      dependencies: Vector[Dependency],
      scalacOptions: Vector[String],
      platform: Platform
  )

  def writeBuildFiles(
      tool: BuildTool,
      out: Path,
      sourceRoots: Vector[String],
      macroSourceRoots: Vector[String]
  ): Unit = {
    val macroModules = collectMacroModules(macroSourceRoots)

    tool match {
      case BuildTool.Sbt(scalaVersion, sbtVersion, dependencies, scalacOptions, platform) =>
        val settings = toSettings(
          scalaVersion = scalaVersion,
          dependencies = dependencies,
          scalacOptions = scalacOptions,
          platform = platform
        )
        writeFile(
          out.resolve("build.sbt"),
          renderSbtBuild(
            settings = settings,
            sourceRoots = sourceRoots,
            macroModules = macroModules,
            sbtVersion = sbtVersion
          )
        )
        writeFile(out.resolve("project/build.properties"), s"sbt.version=$sbtVersion\n")
        renderSbtPluginDeclaration(platform).foreach(plugins => writeFile(out.resolve("project/plugins.sbt"), plugins))

      case BuildTool.Mill(scalaVersion, millVersion, dependencies, scalacOptions, platform) =>
        val settings = toSettings(
          scalaVersion = scalaVersion,
          dependencies = dependencies,
          scalacOptions = scalacOptions,
          platform = platform
        )
        writeFile(
          out.resolve("build.mill"),
          renderMillBuild(settings = settings, sourceRoots = sourceRoots, macroModules = macroModules)
        )
        writeFile(out.resolve(".mill-version"), s"$millVersion\n")
    }
  }

  private def toSettings(
      scalaVersion: String,
      dependencies: Vector[Dependency],
      scalacOptions: Vector[String],
      platform: Platform
  ): Settings =
    Settings(
      scalaVersion = scalaVersion,
      dependencies = ToolchainLibraries.dropToolchainLibraries(dependencies),
      scalacOptions = ScalacOptions.filterForSlice(scalacOptions),
      platform = platform
    )

  private def renderSbtPluginDeclaration(platform: Platform): Option[String] = platform match {
    case Platform.Jvm => None
    case Platform.ScalaJs(version) =>
      Some(renderAddSbtPlugin(organization = "org.scala-js", artifact = "sbt-scalajs", version = version))
    case Platform.ScalaNative(version) =>
      Some(renderAddSbtPlugin(organization = "org.scala-native", artifact = "sbt-scala-native", version = version))
  }

  private def renderAddSbtPlugin(organization: String, artifact: String, version: String): String =
    s"""addSbtPlugin("$organization" % "$artifact" % "$version")\n"""

  private def sbtPlatformPluginName(platform: Platform): Option[String] = platform match {
    case Platform.Jvm            => None
    case _: Platform.ScalaJs     => Some("ScalaJSPlugin")
    case _: Platform.ScalaNative => Some("ScalaNativePlugin")
  }

  private def renderSbtEnablePlugins(platform: Platform): String =
    sbtPlatformPluginName(platform).map(plugin => s"enablePlugins($plugin)\n").getOrElse("")

  private def renderSbtProjectEnablePlugins(platform: Platform): String =
    sbtPlatformPluginName(platform).map(plugin => s"\n  .enablePlugins($plugin)").getOrElse("")

  private def millModuleTypeName(platform: Platform): String = platform match {
    case Platform.Jvm            => "ScalaModule"
    case _: Platform.ScalaJs     => "ScalaJSModule"
    case _: Platform.ScalaNative => "ScalaNativeModule"
  }

  private def renderMillPlatformImport(platform: Platform): String = platform match {
    case Platform.Jvm            => ""
    case _: Platform.ScalaJs     => "import mill.scalajslib.*\n"
    case _: Platform.ScalaNative => "import mill.scalanativelib.*\n"
  }

  private def renderMillPlatformVersion(platform: Platform, indent: String): String = platform match {
    case Platform.Jvm                  => ""
    case Platform.ScalaJs(version)     => indent + s"""def scalaJSVersion = "$version"\n"""
    case Platform.ScalaNative(version) => indent + s"""def scalaNativeVersion = "$version"\n"""
  }

  private def wrapIfNonEmpty(rendered: String, before: String, after: String): String =
    if (rendered.isEmpty) "" else before + rendered + after

  private def renderSbtSourceDirectories(sourceRoots: Vector[String], base: String, indent: String): String =
    if (sourceRoots.isEmpty)
      SourceLayout.defaultSourceRootSegments
        .map(quoted)
        .mkString(s"Seq($base / ", " / ", ")")
    else
      sourceRoots
        .map(root => indent + base + " / " + root.split('/').map(quoted).mkString(" / "))
        .mkString("Seq(\n", ",\n", "\n" + indent.dropRight(2) + ")")

  private def renderSbtLibraries(settings: Settings, indent: String, sbtVersion: String): String =
    if (settings.dependencies.isEmpty) ""
    else
      settings.dependencies
        .map(dependency => indent + dependency.renderSbtSyntax(settings.platform, platformAppliedBySbt(sbtVersion)))
        .mkString("libraryDependencies ++= Seq(\n", ",\n", "\n" + indent.dropRight(2) + ")")

  def platformAppliedBySbt(sbtVersion: String): Boolean = !sbtVersion.startsWith("1.")

  private def renderSbtScalacOptions(scalacOptions: Vector[String], indent: String): String =
    if (scalacOptions.isEmpty) ""
    else
      scalacOptions
        .map(option => indent + quoted(option))
        .mkString("scalacOptions ++= Seq(\n", ",\n", "\n" + indent.dropRight(2) + ")")

  private def renderSbtBuild(
      settings: Settings,
      sourceRoots: Vector[String],
      macroModules: Vector[MacroModule],
      sbtVersion: String
  ): String =
    if (macroModules.isEmpty)
      renderSbtSingleModule(settings = settings, sourceRoots = sourceRoots, sbtVersion = sbtVersion)
    else
      renderSbtWithMacroModules(
        settings = settings,
        sourceRoots = sourceRoots,
        macroModules = macroModules,
        sbtVersion = sbtVersion
      )

  private def renderSbtSingleModule(settings: Settings, sourceRoots: Vector[String], sbtVersion: String): String = {
    val options =
      wrapIfNonEmpty(rendered = renderSbtScalacOptions(settings.scalacOptions, "  "), before = "", after = "\n\n")
    val libraries = wrapIfNonEmpty(
      rendered = renderSbtLibraries(settings = settings, indent = "  ", sbtVersion = sbtVersion),
      before = "",
      after = "\n\n"
    )
    val plugins = wrapIfNonEmpty(rendered = renderSbtEnablePlugins(settings.platform), before = "", after = "\n")

    s"""scalaVersion := "${settings.scalaVersion}"
       |name := "slice"
       |
       |$plugins$options${libraries}Compile / unmanagedSourceDirectories := ${renderSbtSourceDirectories(
        sourceRoots = sourceRoots,
        base = "baseDirectory.value",
        indent = "  "
      )}
       |""".stripMargin
  }

  private def collectMacroModules(macroSourceRoots: Vector[String]): Vector[MacroModule] =
    macroSourceRoots
      .groupBy(toModuleDirectory)
      .toVector
      .collect { case (Some(directory), roots) =>
        MacroModule(directory = directory, name = toModuleIdentifier(directory), sourceRoots = roots.sorted)
      }
      .sortBy(_.directory)

  private def toModuleDirectory(sourceRoot: String): Option[String] = {
    val suffix = "/" + SourceLayout.defaultSourceRoot
    if (sourceRoot.endsWith(suffix)) Some(sourceRoot.dropRight(suffix.length)) else None
  }

  private def toModuleIdentifier(directory: String): String =
    directory
      .split('/')
      .lastOption
      .getOrElse(directory)
      .map(character => if (character.isLetterOrDigit) character else '_')

  private def dropModulePrefix(module: MacroModule, sourceRoot: String): String =
    sourceRoot.drop(module.directory.length + 1)

  private def renderSbtWithMacroModules(
      settings: Settings,
      sourceRoots: Vector[String],
      macroModules: Vector[MacroModule],
      sbtVersion: String
  ): String = {
    val libraries =
      wrapIfNonEmpty(
        rendered = renderSbtLibraries(settings = settings, indent = "      ", sbtVersion = sbtVersion),
        before = "    ",
        after = ",\n"
      )
    val options =
      wrapIfNonEmpty(
        rendered = renderSbtScalacOptions(settings.scalacOptions, "      "),
        before = "    ",
        after = ",\n"
      )
    val enabled = renderSbtProjectEnablePlugins(settings.platform)

    val projects = macroModules.map { module =>
      val directories = module.sourceRoots
        .map(root =>
          "      baseDirectory.value / " + dropModulePrefix(module, root).split('/').map(quoted).mkString(" / ")
        )
        .mkString("Seq(\n", ",\n", "\n    )")

      s"""lazy val ${module.name} = (project in file(${quoted(module.directory)}))$enabled
         |  .settings(
         |    scalaVersion := "${settings.scalaVersion}",
         |$options$libraries    Compile / unmanagedSourceDirectories := $directories
         |  )
         |""".stripMargin
    }

    val dependsOn = macroModules.map(_.name).mkString(".dependsOn(", ", ", ")")

    s"""${projects.mkString("\n")}
       |lazy val root = (project in file("."))$enabled
       |  $dependsOn
       |  .settings(
       |    name := "slice",
       |    scalaVersion := "${settings.scalaVersion}",
       |$options$libraries    Compile / unmanagedSourceDirectories := ${renderSbtSourceDirectories(
        sourceRoots = sourceRoots,
        base = "baseDirectory.value",
        indent = "      "
      )}
       |  )
       |""".stripMargin
  }

  private def renderMillSources(sourceRoots: Vector[String]): String =
    if (sourceRoots.isEmpty) quoted(SourceLayout.defaultSourceRoot)
    else sourceRoots.map(quoted).mkString(", ")

  private def renderMillModuleSources(module: MacroModule): String =
    module.sourceRoots
      .map(root => dropModulePrefix(module, root).split('/').map(quoted).mkString("moduleDir / ", " / ", ""))
      .mkString(", ")

  private def renderMillDependencies(dependencies: Vector[Dependency], task: String, indent: String): String =
    if (dependencies.isEmpty) ""
    else
      dependencies
        .map(dependency => indent + "  " + dependency.renderMillSyntax)
        .mkString(indent + s"def $task = Seq(\n", ",\n", "\n" + indent + ")\n")

  private def renderMillLibraries(dependencies: Vector[Dependency], indent: String): String =
    renderMillDependencies(
      dependencies = Dependency.filterToScope(dependencies, DependencyScope.Compile),
      task = "mvnDeps",
      indent = indent
    ) +
      renderMillDependencies(
        dependencies = Dependency.filterToScope(dependencies, DependencyScope.Provided),
        task = "compileMvnDeps",
        indent = indent
      ) +
      renderMillDependencies(
        dependencies = Dependency.filterToScope(dependencies, DependencyScope.Plugin),
        task = "scalacPluginMvnDeps",
        indent = indent
      )

  private def renderMillScalacOptions(scalacOptions: Vector[String], indent: String): String =
    if (scalacOptions.isEmpty) ""
    else scalacOptions.map(quoted).mkString(indent + "def scalacOptions = Seq(", ", ", ")\n")

  private def renderMillSettings(settings: Settings, indent: String): String =
    indent + s"""def scalaVersion = "${settings.scalaVersion}"\n""" +
      renderMillPlatformVersion(settings.platform, indent) +
      renderMillScalacOptions(settings.scalacOptions, indent) +
      renderMillLibraries(settings.dependencies, indent)

  private def renderMillBuild(
      settings: Settings,
      sourceRoots: Vector[String],
      macroModules: Vector[MacroModule]
  ): String = {
    val moduleType = millModuleTypeName(settings.platform)

    val moduleDeps =
      if (macroModules.isEmpty) ""
      else macroModules.map(module => s"`${module.name}`").mkString("  def moduleDeps = Seq(", ", ", ")\n")

    val nested =
      if (macroModules.isEmpty) ""
      else
        "\n" + macroModules
          .map(module =>
            s"""  object `${module.name}` extends $moduleType {
               |${renderMillSettings(settings, "    ")}    def sources = Task.Sources(${renderMillModuleSources(
                module
              )})
               |  }
               |""".stripMargin
          )
          .mkString("\n")

    s"""package build
       |
       |import mill.*
       |import mill.scalalib.*
       |${renderMillPlatformImport(settings.platform)}
       |object `package` extends $moduleType {
       |${renderMillSettings(settings, "  ")}$moduleDeps  def sources = Task.Sources(${renderMillSources(sourceRoots)})
       |$nested}
       |""".stripMargin
  }

  private def quoted(value: String): String = "\"" + value + "\""

  private def writeFile(file: Path, content: String): Unit = {
    Files.createDirectories(file.getParent)
    Files.write(file, content.getBytes(StandardCharsets.UTF_8)): Unit
  }
}
