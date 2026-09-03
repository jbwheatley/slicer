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

import scala.concurrent.duration.*

import mill.testkit.IntegrationTester

class MillModuleSuite extends munit.FunSuite {

  override def munitTimeout: Duration = 2.minute

  private val pluginClasspath: Vector[os.Path] =
    sys.props("slicer.pluginClasspath").split(java.io.File.pathSeparator).toVector.map(os.Path(_)).filter(os.exists)

  private val corpus: os.Path =
    os.Path(sys.props.getOrElse("slicer.millCorpus", "slicer-core/src/test/test-project"), os.pwd)

  private val fixture = FunFixture[IntegrationTester](
    setup = _ =>
      IntegrationTester(
        daemonMode = false,
        workspaceSourcePath = buildFixture(os.temp.dir(prefix = "slice-mill-source-")),
        millExecutable = corpus / "mill",
        baseWorkspacePath = os.temp.dir(prefix = "slice-mill-workspace-")
      ),
    teardown = tester => {
      tester.close()
      os.remove.all(tester.workspaceSourcePath)
      os.remove.all(tester.baseWorkspacePath)
    }
  )

  private def buildFixture(directory: os.Path): os.Path = {
    Vector("base", "external", "entry").foreach(module => os.copy.into(corpus / module, directory))
    os.copy.into(corpus / ".mill-version", directory)

    os.write(
      directory / "mill-build" / "build.mill",
      s"""package build
         |
         |import mill.*
         |import mill.meta.MillBuildRootModule
         |
         |object `package` extends MillBuildRootModule {
         |  def unmanagedClasspath = Task {
         |    super.unmanagedClasspath() ++ Seq(${pluginClasspath
          .map(entry => s"""PathRef(os.Path("$entry"))""")
          .mkString(", ")})
         |  }
         |}
         |""".stripMargin,
      createFolders = true
    )

    os.write(
      directory / "build.mill",
      s"""package build
         |
         |import mill.*
         |import mill.scalalib.*
         |import slicer.mill.SlicerModule
         |
         |trait Spec extends SlicerModule {
         |  def scalaVersion = "${scalaVersionOfCorpus()}"
         |}
         |
         |object base extends Spec
         |
         |object external extends Spec {
         |  def moduleDeps = Seq(base)
         |  def mvnDeps = Seq(mvn"org.typelevel::cats-core:2.13.0")
         |}
         |
         |object entry extends Spec {
         |  def moduleDeps = Seq(base, external)
         |}
         |""".stripMargin
    )
    directory
  }

  private def scalaVersionOfCorpus(): String =
    """scalaVersion\s*=\s*"([^"]+)"""".r
      .findFirstMatchIn(os.read(corpus / "build.mill"))
      .map(_.group(1))
      .getOrElse(fail(s"no scalaVersion in ${corpus / "build.mill"}"))

  fixture.test("a mill build that mixes in SlicerModule offers the slice task") { tester =>
    val result = tester.eval(("resolve", "entry._"))
    assert(result.isSuccess, result.debugString)
    assert(result.out.linesIterator.contains("entry.slice"), result.debugString)
  }
}
