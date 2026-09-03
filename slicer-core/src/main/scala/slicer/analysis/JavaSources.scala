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

package slicer.analysis

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.annotation.tailrec
import scala.util.matching.Regex

import slicer.model.{DefKind, DefNode, SliceFailure, Symbol}

import cats.syntax.either.*
import cats.syntax.eq.*

private[slicer] object JavaSources {

  private final case class RawType(name: String, start: Int, bodyStart: Int, bodyEnd: Int)

  private final case class TypeDeclaration(
      name: String,
      symbol: Symbol,
      owner: Option[Symbol],
      start: Int,
      end: Int
  )

  private lazy val packagePattern: Regex = """\bpackage\s+([\w.]+)\s*;""".r

  private lazy val importPattern: Regex = """\bimport\s+(?:static\s+)?([\w.$]+?)(\.\*)?\s*;""".r

  private lazy val typePattern: Regex = """\b(?:class|interface|enum|record|@interface)\s+(\w+)""".r

  private lazy val referencePattern: Regex = """\b([A-Z][\w$]*)\b""".r

  def isJavaFile(file: Path): Boolean = file.getFileName.toString.endsWith(".java")

  def listJavaFilesUnder(sourceDirs: Vector[Path]): Vector[Path] =
    sourceDirs.distinct
      .flatMap(SourceFiles.listSourceFilesUnder)
      .filter(isJavaFile)
      .map(_.normalize())
      .distinct
      .sortBy(_.toString)

  def readJavaFile(file: Path): Either[SliceFailure, JavaFileData] =
    Either
      .catchNonFatal(String(Files.readAllBytes(file), StandardCharsets.UTF_8))
      .leftMap(failure => SliceFailure(s"could not read $file", failure))
      .map(text => parseJavaFile(file = file, text = text))

  private def parseJavaFile(file: Path, text: String): JavaFileData = {
    val code = blankCommentsAndLiterals(text)
    val packageName = packagePattern.findFirstMatchIn(code).map(_.group(1)).getOrElse("")
    val types = collectTypeDeclarations(code = code, packageName = packageName)
    val nodes = types.map(toJavaTypeNode(file, _))
    val references =
      collectTypeReferences(code = code, packageName = packageName, declared = types.map(_.name).toSet)

    JavaFileData(
      file = file,
      text = text,
      nodes = nodes,
      references = for {
        node <- nodes
        reference <- references
      } yield node.symbol -> reference
    )
  }

  private def toJavaTypeNode(file: Path, declaration: TypeDeclaration): DefNode =
    DefNode(
      symbol = declaration.symbol,
      kind = DefKind.JavaType,
      displayName = declaration.name,
      file = file,
      start = declaration.start,
      end = declaration.end,
      owner = declaration.owner,
      isAbstract = false,
      expandsAtCallSite = false
    )

  private def collectTypeDeclarations(code: String, packageName: String): Vector[TypeDeclaration] = {
    val declarations = typePattern.findAllMatchIn(code).toVector.map { declaration =>
      val (bodyStart, bodyEnd) = findBodyExtent(code, declaration.end)
      RawType(name = declaration.group(1), start = declaration.start, bodyStart = bodyStart, bodyEnd = bodyEnd)
    }

    val prefix = if (packageName.isEmpty) "" else packageName.replace('.', '/') + "/"

    declarations.indices.toVector.map { index =>
      val declaration = declarations(index)
      TypeDeclaration(
        name = declaration.name,
        symbol = buildTypeSymbol(declarations = declarations, prefix = prefix, index = index),
        owner = findEnclosingDeclaration(declarations, declaration.start)
          .map(owner => buildTypeSymbol(declarations = declarations, prefix = prefix, index = owner)),
        start = declaration.start,
        end = declaration.bodyEnd
      )
    }
  }

  private def findEnclosingDeclaration(declarations: Vector[RawType], at: Int): Option[Int] =
    declarations.indices
      .filter(other => declarations(other).bodyStart <= at && at < declarations(other).bodyEnd)
      .sortBy(other => declarations(other).bodyEnd - declarations(other).bodyStart)
      .headOption

  private def buildTypeSymbol(declarations: Vector[RawType], prefix: String, index: Int): Symbol = {
    val declaration = declarations(index)
    findEnclosingDeclaration(declarations, declaration.start) match {
      case Some(owner) =>
        Symbol(
          buildTypeSymbol(declarations = declarations, prefix = prefix, index = owner).value + declaration.name + "#"
        )
      case None => Symbol(prefix + declaration.name + "#")
    }
  }

  private def collectTypeReferences(code: String, packageName: String, declared: Set[String]): Vector[String] = {
    val imports = importPattern.findAllMatchIn(code).toVector
    val (stars, exact) = imports.partition(entry => Option(entry.group(2)).isDefined)
    val packages = (if (packageName.isEmpty) Vector.empty else Vector(packageName)) ++ stars.map(_.group(1))

    val names = referencePattern
      .findAllMatchIn(code)
      .map(_.group(1))
      .toVector
      .distinct
      .filterNot(declared.contains)

    (exact.map(_.group(1)) ++ packages.flatMap(prefix => names.map(name => s"$prefix.$name"))).distinct
  }

  private def findBodyExtent(code: String, from: Int): (Int, Int) =
    code.indexOf('{', from) match {
      case -1   => (from, from)
      case open => (open + 1, findClosingBrace(code = code, at = open, depth = 0))
    }

  @tailrec
  private def findClosingBrace(code: String, at: Int, depth: Int): Int =
    if (at >= code.length) code.length
    else
      code.charAt(at) match {
        case '{'                => findClosingBrace(code = code, at = at + 1, depth = depth + 1)
        case '}' if depth === 1 => at + 1
        case '}'                => findClosingBrace(code = code, at = at + 1, depth = depth - 1)
        case _                  => findClosingBrace(code = code, at = at + 1, depth = depth)
      }

  private enum Scan {
    case Code, LineComment, BlockComment, Text, Character
  }

  private def blankCommentsAndLiterals(text: String): String = {
    val blanks = text.indices.foldLeft((Scan.Code, Vector.empty[Char], false)) { case ((scan, kept, escaped), index) =>
      val character = text.charAt(index)
      val ahead = if (index + 1 < text.length) text.charAt(index + 1) else ' '
      val blank = if (character === '\n') '\n' else ' '

      scan match {
        case Scan.Code if character === '/' && ahead === '/' => (Scan.LineComment, kept :+ blank, false)
        case Scan.Code if character === '/' && ahead === '*' => (Scan.BlockComment, kept :+ blank, false)
        case Scan.Code if character === '"'                  => (Scan.Text, kept :+ blank, false)
        case Scan.Code if character === '\''                 => (Scan.Character, kept :+ blank, false)
        case Scan.Code                                       => (Scan.Code, kept :+ character, false)
        case Scan.LineComment if character === '\n'          => (Scan.Code, kept :+ blank, false)
        case Scan.LineComment                                => (Scan.LineComment, kept :+ blank, false)
        case Scan.BlockComment if character === '/' && index > 0 && text.charAt(index - 1) === '*' =>
          (Scan.Code, kept :+ blank, false)
        case Scan.BlockComment                    => (Scan.BlockComment, kept :+ blank, false)
        case Scan.Text if escaped                 => (Scan.Text, kept :+ blank, false)
        case Scan.Text if character === '\\'      => (Scan.Text, kept :+ blank, true)
        case Scan.Text if character === '"'       => (Scan.Code, kept :+ blank, false)
        case Scan.Text                            => (Scan.Text, kept :+ blank, false)
        case Scan.Character if escaped            => (Scan.Character, kept :+ blank, false)
        case Scan.Character if character === '\\' => (Scan.Character, kept :+ blank, true)
        case Scan.Character if character === '\'' => (Scan.Code, kept :+ blank, false)
        case Scan.Character                       => (Scan.Character, kept :+ blank, false)
      }
    }

    blanks._2.mkString
  }
}
