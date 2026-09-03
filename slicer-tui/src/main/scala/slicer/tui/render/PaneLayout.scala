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

import tui.viewport.TerminalSize

private[tui] final case class PaneLayout(matchWidth: Int, previewWidth: Int, paneHeight: Int, frameDetail: FrameDetail)

private[tui] object PaneLayout {

  val runtimeTailRows: Int = 3

  val rightMarginColumns: Int = 1

  private val paneBorderRows: Int = 2
  private val minimumPaneRows: Int = 1
  private val minimumPaneColumns: Int = 15
  private val readablePreviewColumns: Int = 40
  private val paneGapColumns: Int = 1
  private val comfortablePaneRows: Int = 8

  def fitToTerminal(
      terminal: TerminalSize,
      wantedMatchColumnsByHeight: Int => Int,
      frameRowsByDetail: FrameDetail => Int
  ): PaneLayout = {
    val detail = FrameDetail.richestFirst
      .find(level => paneRows(terminal, frameRowsByDetail(level)) >= comfortablePaneRows)
      .getOrElse(smallestFittingDetail(terminal, frameRowsByDetail))
    val paneHeight = paneRows(terminal, frameRowsByDetail(detail))
    val matchWidth = clampMatchColumns(terminal, wantedMatchColumnsByHeight(paneHeight))
    PaneLayout(
      matchWidth = matchWidth,
      previewWidth = math.max(minimumPaneColumns, usableColumns(terminal) - matchWidth),
      paneHeight = paneHeight,
      frameDetail = detail
    )
  }

  private def clampMatchColumns(terminal: TerminalSize, wanted: Int): Int = {
    val usable = usableColumns(terminal)
    val ceiling = math.max(usable - readablePreviewColumns, usable / 2)
    math.max(minimumPaneColumns, math.min(wanted, ceiling))
  }

  private def usableColumns(terminal: TerminalSize): Int =
    terminal.columns - paneGapColumns - rightMarginColumns

  private def smallestFittingDetail(terminal: TerminalSize, frameRowsByDetail: FrameDetail => Int): FrameDetail =
    FrameDetail.richestFirst
      .find(level => totalFramedRows(terminal, frameRowsByDetail(level)) <= terminal.rows)
      .getOrElse(FrameDetail.Bare)

  private def totalFramedRows(terminal: TerminalSize, frameRows: Int): Int =
    frameRows + paneRows(terminal, frameRows) + paneBorderRows + runtimeTailRows

  private def paneRows(terminal: TerminalSize, frameRows: Int): Int =
    math.max(minimumPaneRows, terminal.rows - frameRows - paneBorderRows - runtimeTailRows)
}
