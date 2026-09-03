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

import cats.syntax.eq.*
import tui.viewport.TerminalSize

// scalafix:off DisableSyntax.defaultArgs
class PaneLayoutSuite extends munit.FunSuite {

  private def planFor(columns: Int, wanted: Int, rows: Int = 40): PaneLayout =
    PaneLayout.fitToTerminal(TerminalSize(rows = rows, columns = columns), _ => wanted, _ => 6)

  private val everyWidth: Seq[Int] = 40.to(240)

  test("matches take the room they want before the preview grows") {
    val wanted = 60
    val narrow = planFor(columns = 100, wanted = wanted)
    val wide = planFor(columns = 200, wanted = wanted)
    assertEquals(narrow.matchWidth, 100 - 2 - 40)
    assertEquals(wide.matchWidth, wanted)
    assert(wide.previewWidth > narrow.previewWidth, s"$narrow $wide")
  }

  test("once the names fit, every extra column goes to the preview") {
    val wanted = 30
    val widths = Seq(80, 100, 140, 200).map(columns => planFor(columns = columns, wanted = wanted))
    assertEquals(widths.map(_.matchWidth).distinct, Seq(wanted))
    assertEquals(widths.map(_.previewWidth).sorted, widths.map(_.previewWidth))
    assert(widths.last.previewWidth - widths.head.previewWidth === 120, widths.toString)
  }

  test("the preview keeps a readable width however long the names are") {
    val offenders = 40
      .to(200)
      .map(columns => (columns, planFor(columns = columns, wanted = 500)))
      .filter { case (columns, plan) => plan.previewWidth < math.min(40, columns - 2 - plan.matchWidth) }
    assertEquals(offenders, Seq.empty[(Int, PaneLayout)])
  }

  test("panes never outgrow the terminal") {
    val offenders = 40
      .to(200)
      .map(columns => (columns, planFor(columns = columns, wanted = 500)))
      .filter { case (columns, plan) => plan.matchWidth + plan.previewWidth > columns }
    assertEquals(offenders, Seq.empty[(Int, PaneLayout)])
  }

  test("neither pane ever shrinks as the terminal widens") {
    val offenders = Seq(20, 45, 90, 500).flatMap { wanted =>
      val plans = everyWidth.map(columns => planFor(columns = columns, wanted = wanted))
      val shrinkingMatches = plans.sliding(2).exists(pair => pair.last.matchWidth < pair.head.matchWidth)
      val shrinkingPreview = plans.sliding(2).exists(pair => pair.last.previewWidth < pair.head.previewWidth)
      Option.when(shrinkingMatches)(s"matches shrink at wanted=$wanted") ++
        Option.when(shrinkingPreview)(s"preview shrinks at wanted=$wanted")
    }
    assertEquals(offenders, Seq.empty[String])
  }

  test("the match pane never claims more room than the names need") {
    val offenders = Seq(0, 14, 30, 55).flatMap { wanted =>
      everyWidth
        .map(columns => (columns, planFor(columns = columns, wanted = wanted)))
        .filter { case (_, plan) => plan.matchWidth > math.max(wanted, 15) }
        .map { case (columns, plan) => s"wanted=$wanted columns=$columns match=${plan.matchWidth}" }
    }
    assertEquals(offenders, Seq.empty[String])
  }

  test("panes grow taller with the terminal and never shorter") {
    val heights = 10.to(80).map(rows => planFor(columns = 100, wanted = 40, rows = rows).paneHeight)
    assertEquals(heights.sorted, heights)
    assert(heights.last > heights.head, heights.toString)
  }
}
