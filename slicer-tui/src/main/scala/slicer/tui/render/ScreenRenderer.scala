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

package slicer.tui.render

import java.nio.file.Path

import scala.language.implicitConversions

import slicer.analysis.Index
import slicer.model.{BuildTool, DefNode, OpenCommand}
import slicer.tui.model.{ConfirmationAnswer, SliceOptionLabel, TuiScreen, TuiState}
import slicer.tui.render.ScreenRenderer.*

import cats.syntax.eq.*
import layoutz.*

private[tui] final class ScreenRenderer(index: Index, sourceRoot: Path, out: Path, tool: BuildTool) {

  def renderScreen(state: TuiState): Element = state.screen match {
    case TuiScreen.Options => renderOptionsPanel(state)
    case TuiScreen.Confirm => renderConfirmation(state)
    case TuiScreen.Done    => renderOutcome(state)
    case TuiScreen.Search  => renderSearchPanel(state)
  }

  private final case class PanelFit(width: Int, boxWidth: Int, budget: Int)

  private def toPanelFit(state: TuiState): PanelFit = {
    val width = state.terminal.columns - borderColumns
    PanelFit(
      width = width,
      boxWidth = math.max(width - nestedMarginColumns, 1),
      budget = state.terminal.rows - PaneLayout.runtimeTailRows
    )
  }

  private def renderOutcome(state: TuiState): Element = {
    val fit = toPanelFit(state)
    val width = fit.width
    val boxWidth = fit.boxWidth
    val budget = fit.budget - confirmationHintRows
    val sections = state.outcome.toVector.flatMap {
      case Left(error) => Vector(renderTitledSection(title = "slice failed", lines = Vector(error), width = boxWidth))
      case Right(target) =>
        renderOpenSections(target, boxWidth) match {
          case Vector() =>
            Vector(renderTitledSection(title = "slice written to", lines = Vector(target.toString), width = boxWidth))
          case opening => renderOpenInstruction(boxWidth) +: opening
        }
    }
    val hint = TextFit.padToWidth("[ESC] to slice something else", width).trim
    layout((keepSectionsWithinRows(sections, budget).flatten :+ (hint: Element))*)
  }

  private def renderOpenInstruction(width: Int): Vector[Element] =
    Vector(
      TextFit
        .padToWidth("slice written. copy a command below and run it in a terminal to open it in an editor:", width)
        .trim,
      Text("")
    )

  private def renderOpenSections(target: Path, width: Int): Vector[Vector[Element]] =
    OpenCommand
      .openCommandsForDirectory(target)
      .map(open =>
        renderTitledSection(title = open.editor, lines = ShellLines.wrapCommand(open.command, width), width = width)
      )

  private def renderTitledSection(title: String, lines: Vector[String], width: Int): Vector[Element] =
    ((renderHeaderRule(title, width) +: lines).map(line => (line: Element)) :+ Text(""))

  private def renderHeaderRule(title: String, width: Int): String = {
    val label = s"-- $title "
    label + "-" * math.max(width - label.length, 0)
  }

  private def keepSectionsWithinRows(sections: Vector[Vector[Element]], budget: Int): Vector[Vector[Element]] =
    sections
      .foldLeft((kept = Vector.empty[Vector[Element]], rows = 0)) { (fitted, section) =>
        if (fitted.kept.isEmpty) (kept = Vector(section), rows = section.size)
        else if (fitted.rows + section.size <= budget)
          (kept = fitted.kept :+ section, rows = fitted.rows + section.size)
        else fitted
      }
      .kept

  private def renderSearchPanel(state: TuiState): Element = {
    val plan = PaneLayout.fitToTerminal(
      terminal = state.terminal,
      wantedMatchColumnsByHeight = paneHeight => widestMatchColumns(state, paneHeight),
      frameRowsByDetail = detail => countFrameRows(state, detail)
    )
    val panes = row(renderMatches(state, plan), renderPreview(state, plan))
    val width = measureRenderedWidth(panes)
    val above = elementsAbovePanes(state = state, detail = plan.frameDetail, panesWidth = width)
    layout(((above :+ panes) ++ elementsBelowPanes(detail = plan.frameDetail, panesWidth = width))*)
  }

  private def measureRenderedWidth(element: Element): Int =
    element.render.split("\n", -1).map(line => TextFit.stripStyling(line).length).maxOption.getOrElse(0)

  private def countFrameRows(state: TuiState, detail: FrameDetail): Int =
    (elementsAbovePanes(state = state, detail = detail, panesWidth = state.terminal.columns) ++
      elementsBelowPanes(detail = detail, panesWidth = state.terminal.columns))
      .map(_.height)
      .sum

  private def elementsAbovePanes(state: TuiState, detail: FrameDetail, panesWidth: Int): Vector[Element] = {
    val width = panesWidth - borderColumns
    val header =
      if (!detail.showsAtLeast(FrameDetail.Full)) Vector.empty[Element]
      else
        Vector[Element](
          section("slicer")(
            layout(
              TextFit.padToWidth(s"source root: $sourceRoot", width).trim,
              TextFit.padToWidth(s"build: ${tool.name}, Scala ${tool.scalaVersion}", width).trim,
              TextFit.padToWidth(s"out: $out", width).trim
            )
          )
        )
    val search = renderSearch(state = state, detail = detail, panesWidth = panesWidth)
    val gap = renderGap(detail)
    (if (header.isEmpty) Vector(search) else (header ++ gap) :+ search) ++ gap
  }

  private def renderSearch(state: TuiState, detail: FrameDetail, panesWidth: Int): Element = {
    val inner = math.max(math.min(panesWidth, state.terminal.columns) - borderColumns, 1)
    val (visible, caretColumn) =
      if (state.query.isEmpty) (searchPlaceholder, 0)
      else windowAroundCaret(text = state.query, caret = state.caret, width = inner)
    val line = highlightColumn(TextFit.padToWidth(visible, inner), caretColumn)
    if (detail.showsAtLeast(FrameDetail.Minimal)) box()(line) else (line: Element)
  }

  private def windowAroundCaret(text: String, caret: Int, width: Int): (String, Int) = {
    val start = math.max(0, caret - width + 1)
    val visible = text.slice(start, start + width)
    if (start === 0) (visible, caret) else ("…" + visible.drop(1), caret - start)
  }

  private def highlightColumn(line: String, column: Int): String =
    if (column >= line.length) line
    else line.take(column) + Text(line.charAt(column).toString).style(Style.Reverse).render + line.drop(column + 1)

  private def elementsBelowPanes(detail: FrameDetail, panesWidth: Int): Vector[Element] = {
    val controls = TextFit.padToWidth(searchControls, panesWidth - borderColumns).trim
    renderGap(detail) ++ Vector[Element](controls)
  }

  private def renderGap(detail: FrameDetail): Vector[Element] =
    if (detail.showsAtLeast(FrameDetail.Compact)) Vector(Text("")) else Vector.empty

  private def widestMatchColumns(state: TuiState, paneHeight: Int): Int =
    visibleMatches(state, paneHeight)
      .map(node => node.dottedName.length + rowPrefixColumns)
      .maxOption
      .getOrElse(0)

  private def topMatchIndex(state: TuiState, paneHeight: Int): Int =
    math.max(0, math.min(state.cursor - paneHeight / 2, state.matches.size - paneHeight))

  private def visibleMatches(state: TuiState, paneHeight: Int): Vector[DefNode] = {
    val top = topMatchIndex(state, paneHeight)
    state.matches.slice(top, top + paneHeight)
  }

  private def renderMatches(state: TuiState, plan: PaneLayout): Element = {
    val height = plan.paneHeight
    val top = topMatchIndex(state, height)
    val window = visibleMatches(state, height)
    val rows = window.zipWithIndex.map { case (node, offset) =>
      val marker = if (top + offset === state.cursor) ">" else " "
      val prefix = s"$marker ${node.kind.keyword.padTo(kindColumns, ' ')} "
      TextFit.padToWidth(
        prefix + TextFit.truncateKeepingTail(node.dottedName, plan.matchWidth - prefix.length),
        plan.matchWidth
      )
    }
    section("matches")(
      layout(padRowsToWidth(rows = rows, width = plan.matchWidth, height = height).map(row => (row: Element))*)
    )
  }

  private def renderPreview(state: TuiState, plan: PaneLayout): Element = {
    val inner = plan.previewWidth - borderColumns
    val blank = " " * inner
    val height = plan.paneHeight - 1
    state.selected match {
      case None =>
        box("source")(
          padToHeight(lines = Vector(TextFit.padToWidth("nothing selected", inner)), blank = blank, height = height)
            .map(line => (line: Element))*
        )
      case Some(node) =>
        val lines = CodePreview.renderCodePreview(index = index, node = node, maxLines = height - 2, maxWidth = inner)
        box(TextFit.truncateKeepingTail(toRelativeFileName(node), math.max(inner - titleMarginColumns, 0)))(
          padToHeight(lines = lines, blank = blank, height = height).map(line => (line: Element))*
        )
    }
  }

  private def padToHeight(lines: Vector[String], blank: String, height: Int): Vector[String] =
    ((blank +: lines.take(math.max(height - 2, 0))) :+ blank).take(math.max(height, 1))

  private def padRowsToWidth(rows: Vector[String], width: Int, height: Int): Vector[String] =
    rows.take(height) ++ Vector.fill(math.max(height - rows.size, 0))(" " * width)

  private def dropTrailingBlanks(line: String): String =
    line.take(line.lastIndexWhere(_ =!= ' ') + 1)

  private def toRelativeFileName(node: DefNode): String =
    if (node.file.startsWith(sourceRoot)) sourceRoot.relativize(node.file).toString else node.file.toString

  private def renderOptionsPanel(state: TuiState): Element = {
    val fit = toPanelFit(state)
    val width = fit.width
    val boxWidth = fit.boxWidth
    val budget = fit.budget
    val rows = splitAndPadLines(
      multiChoice(
        label = "options",
        options = SliceOptionLabel.values.map(_.label).toVector,
        selected = state.enabledOptions.map(_.ordinal),
        cursor = state.optionCursor.ordinal,
        active = true
      ),
      width
    ).map(line => (dropTrailingBlanks(line): Element))
    val hint = TextFit.padToWidth("[ESC] to return to search", width).trim
    val panel = Vector[Element](layout(rows*))
    val explanation = explainSelectedOption(state = state, width = boxWidth, budget = budget - rows.size)
    val about =
      if (explanation.isEmpty) Vector.empty[Element]
      else Vector[Element](Text(""), box()(explanation.map(line => (TextFit.padToWidth(line, boxWidth): Element))*))
    val aboutRows = if (explanation.isEmpty) 0 else explanation.size + boxRows
    val tail =
      if (budget >= rows.size + aboutRows + optionsHintRows) Vector[Element](Text(""), hint)
      else Vector.empty[Element]
    layout((panel ++ about ++ tail)*)
  }

  private def renderConfirmation(state: TuiState): Element = {
    val fit = toPanelFit(state)
    val width = fit.width
    val boxWidth = fit.boxWidth
    val budget = fit.budget
    val asked = splitAndPadLines(
      singleChoice(
        label = TextFit
          .truncateKeepingTail(renderConfirmationQuestion(state), boxWidth - confirmationLabelColumns),
        options = ConfirmationAnswer.all.map(_.label),
        selected = state.answer.ordinal,
        active = true
      ),
      boxWidth
    )
    val target = state.selected.toVector.map(node =>
      TextFit.truncateKeepingTail(s"  into ${out.resolve(node.symbol.toDirectoryName)}", boxWidth)
    )
    val room = math.max(budget - boxRows, 1)
    val wanted = (asked.take(1) ++ target) ++ asked.drop(1)
    val lines = if (wanted.size <= room) wanted else asked.take(room)
    val hint = TextFit.padToWidth("[ENTER] to answer", width).trim
    val dialog = Vector[Element](box()(lines.map(line => (TextFit.padToWidth(line, boxWidth): Element))*))
    val tail =
      if (budget >= lines.size + boxRows + confirmationHintRows)
        Vector[Element](Text(""), hint)
      else Vector.empty[Element]
    layout((dialog ++ tail)*)
  }

  private def renderConfirmationQuestion(state: TuiState): String =
    state.selected match {
      case Some(node) => s"slice ${node.dottedName}"
      case None       => "nothing selected"
    }

  private def splitAndPadLines(element: Element, width: Int): Vector[String] =
    element.render.split("\n", -1).toVector.map(line => TextFit.padToWidth(line, width))

  private def explainSelectedOption(state: TuiState, width: Int, budget: Int): Vector[String] = {
    val wrapped = wrapToWidth(state.optionCursor.detail, width)
    val room = budget - boxRows
    if (wrapped.size <= room) wrapped else Vector.empty
  }

  private def wrapToWidth(text: String, width: Int): Vector[String] =
    if (width <= 0) Vector.empty
    else
      text.split(" ").toVector.foldLeft(Vector.empty[String]) { (lines, word) =>
        lines.lastOption match {
          case Some(line) if line.length + 1 + word.length <= width => lines.dropRight(1) :+ s"$line $word"
          case _                                                    => lines :+ word.take(width)
        }
      }
}

private[tui] object ScreenRenderer {

  private val borderColumns: Int = 4

  private[tui] val boxRows: Int = 3

  private val titleMarginColumns: Int = 2

  private val nestedMarginColumns: Int = 2

  private val kindColumns: Int = 10

  private val rowPrefixColumns: Int = kindColumns + 3

  private val optionsHintRows: Int = 5

  private val confirmationHintRows: Int = 2

  private val confirmationLabelColumns: Int = 2

  private val searchControls: String = "[ENTER] to select slice | [Ctrl+O] for options"

  private val searchPlaceholder: String =
    "type to search - a leading def/class/trait etc. filters by kind..."
}
