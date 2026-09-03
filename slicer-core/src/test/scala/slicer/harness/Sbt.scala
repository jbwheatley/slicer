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

package slicer.harness

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*
import scala.sys.process.*
import scala.util.Using

import cats.syntax.eq.*

final class Sbt extends ScalaCompiler {

  override def compileDirectory(directory: Path, compiledFirst: Set[Path]): CompileResult = {
    val output = StringBuilder()
    val logger = ProcessLogger(line => output.append(line).append('\n'): Unit)
    val status = Process(Sbt.compileCommand, directory.toFile).!(logger)
    Process(Sbt.shutdownCommand, directory.toFile).!(logger): Unit
    val lines = output.toString.linesIterator.toVector
    val errors = lines.filter(_.startsWith("[error]"))
    CompileResult(ok = status === 0, if (errors.isEmpty) lines.takeRight(5) else errors)
  }
}

object Sbt {

  private val compileCommand: Seq[String] = Seq("sbt", "--no-colors", "-batch", "compile")

  private val shutdownCommand: Seq[String] = Seq("sbt", "--no-colors", "-batch", "shutdown")

  def compiledClassesIn(out: Path): Vector[Path] = {
    val target = out.resolve("target")
    if (!Files.isDirectory(target)) Vector.empty
    else Using.resource(Files.walk(target))(_.iterator().asScala.filter(_.toString.endsWith(".class")).toVector)
  }
}
