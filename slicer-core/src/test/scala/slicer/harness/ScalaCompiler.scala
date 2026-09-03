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
import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters.*
import scala.sys.process.*
import scala.util.Using

trait ScalaCompiler extends AutoCloseable {

  def compileDirectory(directory: Path, compiledFirst: Set[Path]): CompileResult

  override def close(): Unit = ()
}

object ScalaCompiler {

  def forCorpus(corpus: Corpus): ScalaCompiler =
    if (corpus.scalaVersion.startsWith("2")) Nsc(corpus) else Dotc(corpus)

  def sourcesIn(directory: Path): Vector[String] =
    Using.resource(Files.walk(directory))(
      _.iterator().asScala
        .filter(source => source.toString.endsWith(".scala") || source.toString.endsWith(".java"))
        .map(_.toString)
        .toVector
        .sorted
    )

  def classpathFor(corpus: Corpus, compilerCoordinate: String): String =
    fetched(
      corpus = corpus,
      coordinates =
        compilerCoordinate +: corpus.classpathDependencies.map(_.toCoordinate(corpus.scalaVersion, corpus.platform)),
      intransitive = false,
      cacheName = s"compiler-${corpus.scalaVersion}.classpath"
    )

  def pluginOptionsFor(corpus: Corpus): Vector[String] =
    corpus.pluginDependencies.map { dependency =>
      val jars = fetched(
        corpus = corpus,
        coordinates = Vector(dependency.toCoordinate(corpus.scalaVersion, corpus.platform)),
        intransitive = true,
        cacheName = s"plugin-${dependency.artifact}-${corpus.scalaVersion}.classpath"
      )
      s"-Xplugin:$jars"
    }

  private def fetched(
      corpus: Corpus,
      coordinates: Vector[String],
      intransitive: Boolean,
      cacheName: String
  ): String = {
    val cache = corpus.repoRoot.resolve(s"target/$cacheName")
    val wanted = coordinates.sorted.mkString(" ")
    val cached =
      if (Files.exists(cache) && Files.size(cache) > 0) Files.readString(cache).linesIterator.toVector
      else Vector.empty
    cached match {
      case Vector(`wanted`, entries) => entries
      case _ =>
        val flags = if (intransitive) Seq("--classpath", "--intransitive") else Seq("--classpath")
        val fetchedEntries = (Seq("cs", "fetch") ++ flags ++ coordinates).!!.trim
        Files.createDirectories(cache.getParent)
        Files.writeString(cache, s"$wanted\n$fetchedEntries\n")
        fetchedEntries
    }
  }

  def loaderFor(classpath: String): URLClassLoader = {
    val urls = classpath.split(java.io.File.pathSeparator).map(p => Paths.get(p).toUri.toURL)
    URLClassLoader(urls, null) // scalafix:ok DisableSyntax.null
  }
}
