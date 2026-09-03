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

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import scala.sys.process.*
import scala.util.Using

import cats.syntax.eq.*

final class Mill(corpus: Corpus) extends ScalaCompiler {

  private lazy val launcher: String = corpus.projectRoot.resolve("mill").toString

  override def compileDirectory(directory: Path, compiledFirst: Set[Path]): CompileResult = {
    val output = ArrayBuffer[String]()
    val logger = ProcessLogger { line => output.append(line) }
    val status = Process(Seq(launcher, "--no-daemon", "compile"), directory.toFile).!(logger)
    val lines = output.toVector
    val errors = lines.filter(_.toLowerCase.contains("error"))
    CompileResult(ok = status === 0, if (errors.isEmpty) lines.takeRight(5) else errors)
  }
}

object Mill {

  def compiledClassesIn(out: Path): Vector[Path] = {
    val classes = out.resolve("out/compile.dest/classes")
    if (!Files.isDirectory(classes)) Vector.empty
    else Using.resource(Files.walk(classes))(_.iterator().asScala.filter(_.toString.endsWith(".class")).toVector)
  }
}
