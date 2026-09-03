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

import java.net.URLClassLoader
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import cats.syntax.eq.*

final class Nsc(corpus: Corpus) extends ScalaCompiler {

  private lazy val classpath: String =
    ScalaCompiler.classpathFor(corpus, s"org.scala-lang:scala-compiler:${corpus.scalaVersion}")

  private lazy val loader: URLClassLoader = ScalaCompiler.loaderFor(classpath)

  override def close(): Unit = loader.close()

  private lazy val settingsClass: Class[?] = loader.loadClass("scala.tools.nsc.Settings")
  private lazy val reporterClass: Class[?] = loader.loadClass("scala.tools.nsc.reporters.Reporter")
  private lazy val storeReporterClass: Class[?] = loader.loadClass("scala.tools.nsc.reporters.StoreReporter")
  private lazy val globalClass: Class[?] = loader.loadClass("scala.tools.nsc.Global")
  private lazy val runClass: Class[?] = loader.loadClass("scala.tools.nsc.Global$Run")

  private lazy val converters: Class[?] = loader.loadClass("scala.jdk.javaapi.CollectionConverters")

  private lazy val toScalaList: java.util.List[String] => Object = {
    val asScala = converters.getMethod("asScala", classOf[java.util.List[?]])
    val toList = loader.loadClass("scala.collection.IterableOnceOps").getMethod("toList")
    values => toList.invoke(asScala.invoke(null, values)) // scalafix:ok DisableSyntax.null
  }

  private lazy val toJavaCollection: Object => java.util.Collection[Object] = {
    val asJavaCollection = converters.getMethod("asJavaCollection", loader.loadClass("scala.collection.Iterable"))
    values =>
      asJavaCollection.invoke(null, values) match { // scalafix:ok DisableSyntax.null
        case collection: java.util.Collection[Object @unchecked] => collection
        case other => sys.error(s"scalac's reporter answered infos with $other")
      }
  }

  private lazy val listClass: Class[?] = loader.loadClass("scala.collection.immutable.List")

  private def call(target: Object, method: String): Object =
    target.getClass.getMethod(method).invoke(target)

  override def compileDirectory(directory: Path, compiledFirst: Set[Path]): CompileResult = {
    val sources = ScalaCompiler.sourcesIn(directory)
    val staged = compiledFirst.map(_.toString)
    val (macroSources, rest) = sources.partition(staged.contains)
    val out = directory.resolve("_classes")

    if (sources.isEmpty) CompileResult(ok = false, Vector(s"$directory contains no sources"))
    else if (macroSources.isEmpty) run(sources = sources, classpath = classpath, out = out)
    else {
      val macrosCompiled = run(sources = macroSources, classpath = classpath, out = out)
      if (!macrosCompiled.ok) macrosCompiled
      else run(sources = rest, classpath = s"$classpath${java.io.File.pathSeparator}$out", out = out)
    }
  }

  private def run(sources: Vector[String], classpath: String, out: Path): CompileResult = {
    Files.createDirectories(out)

    val settings = settingsClass.getConstructor().newInstance()
    val arguments = Vector("-d", out.toString, "-classpath", classpath) ++
      ScalaCompiler.pluginOptionsFor(corpus) ++ corpus.scalacOptions
    settingsClass
      .getMethod("processArguments", listClass, classOf[Boolean])
      .invoke(settings, toScalaList(arguments.asJava), java.lang.Boolean.TRUE): Unit

    val reporter = storeReporterClass.getConstructor(settingsClass).newInstance(settings)
    val global = globalClass.getConstructor(settingsClass, reporterClass).newInstance(settings, reporter)
    val run = runClass.getConstructor(globalClass).newInstance(global)
    runClass.getMethod("compile", listClass).invoke(run, toScalaList(sources.asJava)): Unit

    val failed = call(reporter, "hasErrors") match {
      case flag: java.lang.Boolean => flag.booleanValue
      case other                   => sys.error(s"scalac's reporter answered hasErrors with $other")
    }
    CompileResult(ok = !failed, errorsIn(call(reporter, "infos")))
  }

  private def errorsIn(infos: Object): Vector[String] =
    toJavaCollection(infos).asScala.toVector.flatMap { info =>
      val severity = call(info, "severity").toString.toLowerCase
      Option.when(severity === "error")(s"${locate(call(info, "pos"))}$severity: ${call(info, "msg")}")
    }

  private def locate(position: Object): String =
    call(position, "isDefined") match {
      case defined: java.lang.Boolean if defined.booleanValue =>
        s"${call(position, "source")}:${call(position, "line")} "
      case _ => ""
    }
}
