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

import java.nio.file.Path

import scala.annotation.tailrec
import scala.meta.*

import slicer.analysis.{Index, JavaSources}
import slicer.model.*

import cats.syntax.eq.*
import cats.syntax.functorFilter.*

private[emit] object Emit {

  private[emit] type Edit = (from: Int, to: Int, replacement: String)

  private type Range = (from: Int, to: Int)

  private enum Removal(val from: Int, val to: Int) {
    case Block(override val from: Int, override val to: Int) extends Removal(from, to)
    case ListItem(override val from: Int, override val to: Int) extends Removal(from, to)
  }

  def writeBuildFiles(out: Path, sliced: Vector[Path], compiledFirst: Set[Path], tool: BuildTool): Unit = {
    val (macroFiles, mainFiles) = sliced.partition(compiledFirst.contains)
    val macroRoots = collectSourceRoots(out, macroFiles)
    val mainRoots = collectSourceRoots(out, mainFiles)

    BuildFileWriter.writeBuildFiles(tool = tool, out = out, sourceRoots = mainRoots, macroSourceRoots = macroRoots)
  }

  private def collectSourceRoots(out: Path, sliced: Vector[Path]): Vector[String] =
    SourceLayout.chooseCompiledRoots(sliced.map(out.relativize).flatMap(SourceLayout.findSourceRoot).distinct)

  def toRelativeSlicePath(sourceRoot: Path, file: Path): Path = {
    val within =
      if (file.startsWith(sourceRoot)) sourceRoot.relativize(file)
      else
        SourceLayout
          .findModuleTail(file)
          .getOrElse(java.nio.file.Paths.get(file.getFileName.toString))

    SourceLayout.findSourceRoot(within) match {
      case Some(_) => within
      case None    => SourceLayout.toGeneratedSourcePath(within)
    }
  }

  def sliceFileText(
      index: Index,
      file: Path,
      kept: Set[Symbol],
      choices: Map[Symbol, Set[Symbol]]
  ): Option[String] = {
    val nodes = index.defsByFile.getOrElse(file, Vector.empty)
    if (!nodes.exists(n => kept(n.symbol))) None
    else if (JavaSources.isJavaFile(file)) Some(index.sources(file))
    else {
      val text = index.sources(file)
      val children = nodes.groupBy(_.owner)
      val roots = nodes.filter(n => n.owner.forall(owner => !nodes.exists(_.symbol === owner)))

      val comments = collectCommentRanges(index = index, file = file)
      val imports = scanImports(index = index, file = file, kept = kept)

      val dropped =
        roots.flatMap(root =>
          collectRemovals(node = root, children = children, kept = kept, text = text, comments = comments)
        ) ++
          collectDeadImports(imports = imports, text = text)

      val removals = dropped ++ collectEmptiedParamClauses(index = index, file = file, removals = dropped)

      val emptied = collectEmptiedBodies(index = index, file = file, removals = removals)

      val cleared =
        removals.map(_.to) ++ emptied.filter(_.replacement.isEmpty).map(_.to)

      val edits =
        toRemovalEdits(text = text, removals = removals) ++
          collectCollapsedImporters(imports) ++
          emptied ++
          collectOrphanedEndMarkers(index = index, file = file, text = text, cleared = cleared) ++
          collectAmbiguityNotes(text = text, nodes = nodes, kept = kept, choices = choices)

      Some(tidyWhitespace(applyEdits(text, edits)))
    }
  }

  private def isKeptOrHasKeptChild(
      node: DefNode,
      children: Map[Option[Symbol], Vector[DefNode]],
      kept: Set[Symbol]
  ): Boolean =
    kept.contains(node.symbol) ||
      children
        .getOrElse(Some(node.symbol), Vector.empty)
        .exists(child => isKeptOrHasKeptChild(node = child, children = children, kept = kept))

  private def collectRemovals(
      node: DefNode,
      children: Map[Option[Symbol], Vector[DefNode]],
      kept: Set[Symbol],
      text: String,
      comments: Map[Int, Int]
  ): Vector[Removal] =
    if (node.kind === DefKind.Binding) Vector.empty
    else if (!isKeptOrHasKeptChild(node = node, children = children, kept = kept))
      Vector(dropRange(text = text, comments = comments, n = node))
    else
      children
        .getOrElse(Some(node.symbol), Vector.empty)
        .flatMap(child =>
          collectRemovals(node = child, children = children, kept = kept, text = text, comments = comments)
        )

  private def collectAmbiguityNotes(
      text: String,
      nodes: Vector[DefNode],
      kept: Set[Symbol],
      choices: Map[Symbol, Set[Symbol]]
  ): Vector[Edit] = {
    val live = choices.toVector.mapFilter { case (member, impls) =>
      val alive = impls.filter(kept)
      Option.when(alive.size > 1)(member -> alive)
    }
    val byStart = nodes.collect { case n if kept.contains(n.symbol) => n.symbol -> n }.toMap

    live.flatMap { case (member, impls) =>
      val names = impls.toVector.map(_.toOwnerName).sorted.mkString(", ")
      val onMember = s"slice: ${impls.size} impls kept ($names) — the wiring is outside the slice"
      toNoteEdit(byStart = byStart, text = text, symbol = member, body = onMember).toVector ++
        impls.toVector.mapFilter { impl =>
          val onImpl = s"slice: one of ${impls.size} impls of ${member.toQualifiedName} kept"
          toNoteEdit(byStart = byStart, text = text, symbol = impl, body = onImpl)
        }
    }
  }

  private def toNoteEdit(byStart: Map[Symbol, DefNode], text: String, symbol: Symbol, body: String): Option[Edit] =
    byStart.get(symbol).flatMap { node =>
      val lineStart = text.lastIndexOf('\n', node.start - 1) + 1
      val indent = text.substring(lineStart, node.start)
      if (indent.forall(_.isWhitespace))
        Some((from = lineStart, to = lineStart, replacement = s"$indent// $body\n"))
      else None
    }

  private def dropRange(text: String, comments: Map[Int, Int], n: DefNode): Removal =
    if (n.kind === DefKind.Param) Removal.ListItem(from = n.start, to = n.end)
    else if (!startsItsLine(text = text, start = n.start)) Removal.Block(from = n.start, to = n.end)
    else
      Removal.Block(from = withLeadingComments(text = text, comments = comments, start = n.start), to = n.end)

  private def startsItsLine(text: String, start: Int): Boolean =
    text.substring(startOfLine(text, start), start).forall(_.isWhitespace)

  private def toRemovalEdits(text: String, removals: Vector[Removal]): Vector[Edit] = {
    val blocks = removals.collect { case block: Removal.Block => (from = block.from, to = block.to) }
    val listItems = removals.collect { case item: Removal.ListItem => (from = item.from, to = item.to) }
    (blocks ++ withListSeparators(text, listItems)).map(range => (from = range.from, to = range.to, replacement = ""))
  }

  private def withListSeparators(text: String, listItems: Vector[Range]): Vector[Range] =
    coalesceRuns(text, listItems.sortBy(range => (range.from, range.to)))
      .map(range => withSurroundingComma(text = text, from = range.from, to = range.to))

  private def coalesceRuns(text: String, sorted: Vector[Range]): Vector[Range] =
    sorted.foldLeft(Vector.empty[Range]) { (runs, range) =>
      runs.lastOption match {
        case Some(last) if range.from <= last.to || isSeparatorsOnly(text.substring(last.to, range.from)) =>
          val joined: Range = (from = last.from, to = math.max(last.to, range.to))
          runs.init :+ joined
        case _ => runs :+ range
      }
    }

  private def isSeparatorsOnly(between: String): Boolean = between.forall(c => c.isWhitespace || c === ',')

  private def collectEmptiedParamClauses(index: Index, file: Path, removals: Vector[Removal]): Vector[Removal] =
    index.trees.get(file) match {
      case Some(tree) =>
        tree
          .collect { case d: Defn.Class => d.ctor.paramClauses.toVector }
          .flatten
          .toVector
          .collect {
            case clause
                if clause.mod.nonEmpty && clause.values.nonEmpty &&
                  clause.values.forall(param => isRemoved(param, removals)) =>
              Removal.Block(from = clause.pos.start, to = clause.pos.end)
          }
      case None => Vector.empty
    }

  private def collectEmptiedBodies(index: Index, file: Path, removals: Vector[Removal]): Vector[Edit] =
    index.trees.get(file) match {
      case Some(tree) =>
        tree
          .collect {
            case d: Defn.Class  => (d, d.templ, false)
            case d: Defn.Trait  => (d, d.templ, false)
            case d: Defn.Object => (d, d.templ, false)
            case d: Defn.Enum   => (d, d.templ, false)
            case d: Defn.Given  => (d, d.templ, true)
          }
          .toVector
          .collect {
            case (definition, templ, needsBody)
                if !isRemoved(definition, removals) &&
                  templ.body.stats.nonEmpty &&
                  templ.body.stats.forall(stat => isRemoved(stat, removals)) =>
              collapseBody(text = index.sources(file), body = templ.body, needsBody = needsBody)
          }
      case None => Vector.empty
    }

  private def collectOrphanedEndMarkers(index: Index, file: Path, text: String, cleared: Vector[Int]): Vector[Edit] =
    index.trees.get(file) match {
      case Some(tree) =>
        tree
          .collect { case marker: Term.EndMarker => marker.pos }
          .toVector
          .collect {
            case pos
                if cleared
                  .exists(from => from <= pos.start && text.substring(from, pos.start).forall(_.isWhitespace)) =>
              (from = startOfLine(text, pos.start), to = endOfLine(text, pos.end), replacement = "")
          }
      case None => Vector.empty
    }

  private def renderEmptyBody(body: Template.Body): String =
    body.selfOpt.filter(_.decltpe.nonEmpty) match {
      case Some(self) => s"{ ${self.syntax.trim.stripSuffix("=>").trim} => }"
      case None       => "{}"
    }

  private def collapseBody(text: String, body: Template.Body, needsBody: Boolean): Edit = {
    val empty = renderEmptyBody(body)
    val keepsBody = needsBody || body.selfOpt.exists(_.decltpe.nonEmpty)
    val start = body.pos.start
    val beforeBody = retreatPast(text = text, from = start, matches = _.isWhitespace)
    val opensWithBrace =
      (start < text.length && text.charAt(start) === '{') || (beforeBody > 0 && text.charAt(beforeBody - 1) === '{')
    val opensWithColon = beforeBody > 0 && text.charAt(beforeBody - 1) === ':'
    val opensWithWith = text.substring(math.max(0, beforeBody - 4), beforeBody) === "with"

    if (opensWithBrace) (from = start, to = body.pos.end, replacement = empty)
    else if (opensWithColon)
      (from = beforeBody - 1, to = body.pos.end, replacement = if (keepsBody) s" $empty" else "")
    else if (opensWithWith) (from = beforeBody - 4, to = body.pos.end, replacement = empty)
    else (from = start, to = body.pos.end, replacement = if (keepsBody) s" $empty" else "")
  }

  private def isRemoved(stat: Tree, removals: Vector[Removal]): Boolean =
    removals.exists(removal => removal.from <= stat.pos.start && stat.pos.end <= removal.to)

  private def collectCommentRanges(index: Index, file: Path): Map[Int, Int] =
    index.trees.get(file) match {
      case Some(tree) =>
        tree.tokens.collect { case comment: Token.Comment => comment.pos.end -> comment.pos.start }.toMap
      case None => Map.empty
    }

  @tailrec
  private def advancePast(text: String, from: Int, matches: Char => Boolean): Int =
    if (from < text.length && matches(text.charAt(from))) advancePast(text = text, from = from + 1, matches = matches)
    else from

  @tailrec
  private def retreatPast(text: String, from: Int, matches: Char => Boolean): Int =
    if (from > 0 && matches(text.charAt(from - 1))) retreatPast(text = text, from = from - 1, matches = matches)
    else from

  private def withSurroundingComma(text: String, from: Int, to: Int): Range = {
    val afterElement = advancePast(text = text, from = to, matches = _.isWhitespace)
    if (afterElement < text.length && text.charAt(afterElement) === ',')
      (from = from, to = advancePast(text = text, from = afterElement + 1, matches = _ === ' '))
    else {
      val beforeElement = retreatPast(text = text, from = from, matches = _.isWhitespace)
      if (beforeElement > 0 && text.charAt(beforeElement - 1) === ',') (from = beforeElement - 1, to = to)
      else (from = from, to = to)
    }
  }

  @tailrec
  private def withLeadingComments(text: String, comments: Map[Int, Int], start: Int): Int =
    startOfCommentAbove(text = text, comments = comments, at = startOfLine(text, start)) match {
      case Some(comment) => withLeadingComments(text = text, comments = comments, start = comment)
      case None          => startOfLine(text, start)
    }

  private def startOfLine(text: String, at: Int): Int = text.lastIndexOf('\n', at - 1) + 1

  private def startOfCommentAbove(text: String, comments: Map[Int, Int], at: Int): Option[Int] = {
    val ending =
      if (at > 0 && text.charAt(at - 1) === '\n')
        retreatPast(text = text, from = at - 1, matches = ch => ch === ' ' || ch === '\t')
      else 0

    comments.get(ending).collect {
      case opening if text.substring(startOfLine(text, opening), opening).forall(_.isWhitespace) =>
        startOfLine(text, opening)
    }
  }

  private def isDeadName(index: Index, kept: Set[Symbol], prefix: String, name: String): Boolean = {
    val candidates = index.byDisplayName
      .getOrElse(name, Vector.empty)
      .filter(d => d.symbol.toPackagePrefix === prefix + "/" || d.dottedName === s"${prefix.replace('/', '.')}.$name")
    candidates.nonEmpty && !candidates.exists(d => kept.contains(d.symbol))
  }

  private def isDeadOwner(index: Index, kept: Set[Symbol], prefix: String): Boolean = {
    val owners = index.byDottedName.getOrElse(prefix.replace('/', '.'), Vector.empty)
    val named = if (owners.nonEmpty) owners else index.byPackage.getOrElse(prefix + "/", Vector.empty)
    named.nonEmpty && !named.exists(d => kept(d.symbol))
  }

  private def isDeadImportee(index: Index, kept: Set[Symbol], prefix: String, importee: Importee): Boolean =
    importee match {
      case Importee.Name(n)      => isDeadName(index = index, kept = kept, prefix = prefix, name = n.value)
      case Importee.Rename(n, _) => isDeadName(index = index, kept = kept, prefix = prefix, name = n.value)
      case _: Importee.Wildcard  => isDeadOwner(index = index, kept = kept, prefix = prefix)
      case _: Importee.GivenAll  => isDeadOwner(index = index, kept = kept, prefix = prefix)
      case _: Importee.Given     => isDeadOwner(index = index, kept = kept, prefix = prefix)
      case _                     => false
    }

  private final case class ScannedImporter(importer: Importer, dead: List[Importee]) {

    private lazy val deadStarts: Set[Int] = dead.map(_.pos.start).toSet

    def whollyDead: Boolean = dead.size === importer.importees.size

    def survivingImportees: List[Importee] =
      importer.importees.filterNot(importee => deadStarts.contains(importee.pos.start))
  }

  private def scanImports(index: Index, file: Path, kept: Set[Symbol]): Vector[(Import, Vector[ScannedImporter])] =
    index.trees.get(file) match {
      case Some(tree) =>
        tree.collect { case imp: Import =>
          val scanned = imp.importers.toVector.map { importer =>
            val prefix = importer.ref.syntax.replace('.', '/')
            ScannedImporter(
              importer = importer,
              dead = importer.importees.filter(importee =>
                isDeadImportee(index = index, kept = kept, prefix = prefix, importee = importee)
              )
            )
          }
          imp -> scanned
        }.toVector
      case None => Vector.empty
    }

  private def collectDeadImports(imports: Vector[(Import, Vector[ScannedImporter])], text: String): Vector[Removal] =
    imports.flatMap { case (imp, scanned) =>
      collectDeadImporters(imp = imp, scanned = scanned, text = text)
    }

  private def collectDeadImporters(imp: Import, scanned: Vector[ScannedImporter], text: String): Vector[Removal] =
    if (scanned.forall(_.whollyDead))
      Vector(Removal.Block(from = imp.pos.start, to = endOfLine(text, imp.pos.end)))
    else scanned.flatMap(collectDeadImportees)

  private def collectDeadImportees(scanned: ScannedImporter): Vector[Removal] =
    if (scanned.whollyDead)
      Vector(Removal.ListItem(from = scanned.importer.pos.start, to = scanned.importer.pos.end))
    else if (collapsesToSingleImportee(scanned)) Vector.empty
    else scanned.dead.toVector.map(d => Removal.ListItem(from = d.pos.start, to = d.pos.end))

  private def collapsesToSingleImportee(scanned: ScannedImporter): Boolean =
    scanned.dead.nonEmpty && !scanned.whollyDead && (scanned.survivingImportees match {
      case (_: Importee.Name) :: Nil => true
      case _                         => false
    })

  private def collectCollapsedImporters(imports: Vector[(Import, Vector[ScannedImporter])]): Vector[Edit] =
    for {
      (_, scanned) <- imports
      importer <- scanned.filter(collapsesToSingleImportee)
      survivor <- importer.survivingImportees.headOption
    } yield (
      from = importer.importer.pos.start,
      to = importer.importer.pos.end,
      replacement = s"${importer.importer.ref.syntax}.${survivor.syntax}"
    )

  private def endOfLine(text: String, from: Int): Int = {
    val newline = text.indexOf('\n', from)
    if (newline >= 0) newline + 1 else text.length
  }

  private def isInsertion(edit: Edit): Boolean = edit.from === edit.to

  private[emit] def applyEdits(text: String, edits: Vector[Edit]): String = {
    val sorted = edits.sortBy(edit => (edit.from, edit.to))
    val merged = sorted.foldLeft(Vector.empty[Edit]) { (accepted, edit) =>
      val overlapsLast =
        accepted.nonEmpty && edit.from <= accepted.last.to &&
          !(isInsertion(edit) && edit.from === accepted.last.to)
      if (overlapsLast) {
        val last = accepted.last
        val joined: Edit =
          (from = last.from, to = math.max(last.to, edit.to), replacement = last.replacement + edit.replacement)
        accepted.init :+ joined
      } else accepted :+ edit
    }
    val cutOut = merged.foldLeft((pieces = Vector.empty[String], cursor = 0)) { (taken, edit) =>
      val keepUntil = math.min(math.max(edit.from, taken.cursor), text.length)
      (
        pieces = taken.pieces :+ text.substring(taken.cursor, keepUntil) :+ edit.replacement,
        cursor = math.min(math.max(edit.to, taken.cursor), text.length)
      )
    }
    (cutOut.pieces :+ text.substring(cutOut.cursor)).mkString
  }

  private def tidyWhitespace(text: String): String =
    text
      .replaceAll("(?m)^[ \\t]+$", "")
      .replaceAll("\n{3,}", "\n\n")
      .trim + "\n"
}
