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

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.net.URLClassLoader
import java.nio.file.{Files, Path}

import scala.collection.mutable

import cats.syntax.eq.*

final class Dotc(corpus: Corpus) extends ScalaCompiler {

  private lazy val classpath: String =
    ScalaCompiler.classpathFor(corpus, s"org.scala-lang:scala3-compiler_3:${corpus.scalaVersion}")

  private lazy val loader: URLClassLoader = ScalaCompiler.loaderFor(classpath)

  override def close(): Unit = loader.close()

  private def call(target: Object, method: String): Object =
    target.getClass.getMethod(method).invoke(target)

  private lazy val compile: (Array[String], Object) => Object = {
    val main =
      loader.loadClass("dotty.tools.dotc.Main$").getField("MODULE$").get(null) // scalafix:ok DisableSyntax.null
    val reporter = loader.loadClass("dotty.tools.dotc.interfaces.SimpleReporter")
    val callback = loader.loadClass("dotty.tools.dotc.interfaces.CompilerCallback")
    val process = main.getClass.getMethod("process", classOf[Array[String]], reporter, callback)
    (args, collector) => process.invoke(main, args, collector, null) // scalafix:ok DisableSyntax.null
  }

  override def compileDirectory(directory: Path, compiledFirst: Set[Path]): CompileResult = {
    val sources = ScalaCompiler.sourcesIn(directory)
    if (sources.isEmpty) CompileResult(ok = false, Vector(s"$directory contains no sources"))
    else {
      val out = directory.resolve("_classes")
      Files.createDirectories(out)
      val errors = mutable.Buffer.empty[String]
      val collector = Proxy.newProxyInstance(
        loader,
        Array(loader.loadClass("dotty.tools.dotc.interfaces.SimpleReporter")),
        new InvocationHandler {
          override def invoke(proxy: Object, method: Method, args: Array[Object]): Object = {
            if (method.getName === "report") errors.synchronized { errors += describe(args(0)): Unit }
            null // scalafix:ok DisableSyntax.null
          }
        }
      )
      val args = Array("-d", out.toString, "-classpath", classpath, "-color:never") ++
        ScalaCompiler.pluginOptionsFor(corpus) ++ corpus.scalacOptions ++ sources
      val reporterResult = compile(args, collector)
      val failed = call(reporterResult, "hasErrors") match {
        case flag: java.lang.Boolean => flag.booleanValue
        case other                   => sys.error(s"dotc's reporter answered hasErrors with $other")
      }
      CompileResult(ok = !failed, errors.toVector)
    }
  }

  private def describe(diagnostic: Object): String = {
    val level = call(diagnostic, "level") match {
      case value: java.lang.Integer => value.intValue
      case other                    => sys.error(s"dotc's diagnostic answered level with $other")
    }
    val message = call(diagnostic, "message")
    val where = call(diagnostic, "position") match {
      case position: java.util.Optional[?] if position.isPresent =>
        val at: Object = position.get
        s"${call(at, "source")}:${call(at, "line")} "
      case _ => ""
    }
    val severity = level match {
      case 2 => "error"
      case 1 => "warning"
      case _ => "info"
    }
    s"$where$severity: $message"
  }
}
