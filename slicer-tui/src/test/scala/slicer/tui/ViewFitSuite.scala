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

package slicer.tui

import java.nio.file.Paths

import slicer.analysis.Index
import slicer.model.{BuildTool, Platform, SliceOptions}
import slicer.tui.model.{SliceOptionLabel, TuiScreen}
import slicer.tui.render.PaneLayout
import slicer.tui.render.ScreenRenderer.boxRows

import cats.syntax.eq.*
import tui.viewport.{TerminalSize, TerminalSizeTracking}

// scalafix:off DisableSyntax.defaultArgs
class ViewFitSuite extends munit.FunSuite {

  private val emptyIndex: Index = SampleIndexes.empty

  private val indexWithLongNames: Index = SampleIndexes.withLongNames

  private val indexWithLongSource: Index = SampleIndexes.withLongSource

  private def renderAt(
      terminal: TerminalSize,
      query: String = "",
      caret: Int = 0,
      cursor: Int = 0,
      screen: TuiScreen = TuiScreen.Search,
      optionCursor: SliceOptionLabel = SliceOptionLabel.FollowImplementations,
      index: Index = emptyIndex
  ): Vector[String] = {
    val tui = SliceTui(
      index = index,
      sourceRoot = Paths.get("/home/someone/a/rather/long/project/path/that/keeps/going"),
      out = Paths.get("/home/someone/a/rather/long/project/path/that/keeps/going/target/slices"),
      tool = BuildTool.Sbt(
        scalaVersion = "3.8.4",
        sbtVersion = "2.0.0",
        dependencies = Vector.empty,
        scalacOptions = Vector.empty,
        platform = Platform.Jvm
      ),
      terminal = terminal,
      sizes = TerminalSizeTracking(),
      tickIntervalMs = 8L,
      query = "",
      options = SliceOptions.default
    )
    val (state, _) = tui.init
    tui
      .view(
        state.copy(
          terminal = terminal,
          query = query,
          caret = caret,
          cursor = cursor,
          screen = screen,
          optionCursor = optionCursor
        )
      )
      .render
      .split("\n", -1)
      .toVector
      .map(stripStyling)
  }

  private def stripStyling(line: String): String = line.replaceAll("\\u001b\\[[0-9;]*m", "")

  private val sizes: Seq[TerminalSize] =
    for {
      rows <- 8 to 80
      columns <- Seq(40, 60, 100, 200)
    } yield TerminalSize(rows = rows, columns = columns)

  private val indexWithMixedNames: Index = SampleIndexes.withMixedNames

  private val corpuses: Seq[(String, Index)] =
    Seq(
      "empty" -> emptyIndex,
      "long names" -> indexWithLongNames,
      "long source" -> indexWithLongSource,
      "mixed names" -> indexWithMixedNames
    )

  test("frame never outgrows the terminal") {
    val offenders = for {
      (name, corpus) <- corpuses
      terminal <- sizes
      height = renderAt(terminal = terminal, index = corpus).size
      offender <- if (height <= terminal.rows) None else Some(s"$name $terminal frame=$height")
    } yield offender
    assertEquals(offenders, Seq.empty[String])
  }

  test("frame leaves room for the runtime quit message and the parked cursor") {
    val offenders = for {
      (name, corpus) <- corpuses
      terminal <- sizes
      height = renderAt(terminal = terminal, index = corpus).size
      budget = terminal.rows - PaneLayout.runtimeTailRows
      offender <- if (height <= budget) None else Some(s"$name $terminal frame=$height budget=$budget")
    } yield offender
    assertEquals(offenders, Seq.empty[String])
  }

  test("no line outruns the terminal width, whatever is in the index") {
    val offenders = for {
      (name, corpus) <- corpuses
      terminal <- sizes
      line <- renderAt(terminal = terminal, index = corpus).filter(_.length > terminal.columns)
    } yield s"$name $terminal |$line|"
    assertEquals(offenders, Seq.empty[String])
  }

  test("the frame keeps its height as the query narrows the matches") {
    val terminal = TerminalSize(rows = 30, columns = 100)
    val heights = Seq("", "o", "on", "one", "zzz")
      .map(query => renderAt(terminal = terminal, query = query, index = indexWithLongSource).size)
      .distinct
    assertEquals(heights.size, 1, heights.toString)
  }

  test("the search box lines up with the panes for every corpus and size") {
    val offenders = for {
      (name, corpus) <- corpuses
      terminal <- sizes
      frame = renderAt(terminal = terminal, index = corpus)
      searchWidth = frame.find(_.startsWith("┌")).map(_.length)
      widest = frame.map(_.length).maxOption
      offender <-
        if (searchWidth.isEmpty || searchWidth === widest) None
        else Some(s"$name $terminal search=${searchWidth.get} widest=${widest.get}")
    } yield offender
    assertEquals(offenders, Seq.empty[String])
  }

  test("a shrinking terminal never grows the frame") {
    val heights = (8 to 80).reverse.map(rows => renderAt(terminal = TerminalSize(rows = rows, columns = 100)).size)
    assertEquals(heights.sorted.reverse, heights)
  }

  test("a long query does not widen the frame") {
    val terminal = TerminalSize(rows = 24, columns = 60)
    val offenders = renderAt(terminal = terminal, query = "a" * 400).filter(_.length > terminal.columns)
    assertEquals(offenders, Vector.empty[String])
  }

  test("the placeholder gives way to what was typed") {
    val terminal = TerminalSize(rows = 24, columns = 100)
    val placeholder = searchLineOf(renderAt(terminal = terminal))
    val typed = searchLineOf(renderAt(terminal = terminal, query = "user"))
    assert(placeholder.trim.nonEmpty, placeholder)
    assert(typed.contains("user"), typed)
    assert(!typed.contains(placeholder.trim), typed)
  }

  private def searchLineOf(frame: Vector[String]): String =
    frame(frame.indexWhere(_.startsWith("┌")) + 1)

  test("the frame keeps its height as the cursor moves between long and short definitions") {
    val offenders = sizes.flatMap { terminal =>
      val heights = 0
        .to(1)
        .map(cursor => renderAt(terminal = terminal, cursor = cursor, index = indexWithLongSource).size)
        .distinct
      if (heights.size === 1) None else Some(s"$terminal heights=${heights.mkString(",")}")
    }
    assertEquals(offenders, Seq.empty[String])
  }

  test("the frame keeps its width as the cursor scrolls between long and short definitions") {
    val offenders =
      Seq(40, 60, 100, 200).map(columns => TerminalSize(rows = 30, columns = columns)).flatMap { terminal =>
        val widths = SampleIndexes.mixedDefinitions.indices.map { cursor =>
          renderAt(terminal = terminal, cursor = cursor, index = indexWithMixedNames).map(_.length).max
        }.distinct
        if (widths.size === 1) None else Some(s"$terminal widths=${widths.mkString(",")}")
      }
    assertEquals(offenders, Seq.empty[String])
  }

  test("the panes fill the terminal width but never touch its last column") {
    val offenders = for {
      (name, corpus) <- corpuses
      terminal <- sizes
      widest = renderAt(terminal = terminal, index = corpus).map(_.length).max
      wanted = terminal.columns - PaneLayout.rightMarginColumns
      offender <- if (widest === wanted) None else Some(s"$name $terminal widest=$widest wanted=$wanted")
    } yield offender
    assertEquals(offenders, Seq.empty[String])
  }

  test("a name too wide for the pane keeps its tail") {
    val frame = renderAt(terminal = TerminalSize(rows = 24, columns = 100), index = indexWithLongNames)
    val row = frame.find(_.contains("soughtAfterMember"))
    assert(row.isDefined, frame.mkString("\n"))
    assert(row.exists(_.contains("\u2026")), row.toString)
    assert(row.exists(_.contains("package.Holder.soughtAfterMember")), row.toString)
    assert(!row.exists(_.contains("com.example.deeply")), row.toString)
  }

  test("a file name too wide for the preview keeps its tail") {
    val frame = renderAt(terminal = TerminalSize(rows = 24, columns = 100), index = indexWithLongNames)
    assert(frame.exists(_.contains("\u2026")), frame.mkString("\n"))
    assert(frame.exists(_.contains("Short.scala")), frame.mkString("\n"))
    assert(!frame.exists(_.contains("/home/someone/project/src")), frame.mkString("\n"))
  }

  test("the caret rides over the query instead of splitting it") {
    val terminal = TerminalSize(rows = 24, columns = 100)
    val carets = 0.to(3).map(caret => renderAt(terminal = terminal, query = "foo", caret = caret))
    val offenders = carets.filterNot(_.exists(_.contains("foo")))
    assertEquals(offenders, Seq.empty[Vector[String]])
  }

  test("the options panel stays inside the terminal") {
    val offenders = sizes.flatMap { terminal =>
      val panel = renderAt(terminal = terminal, screen = TuiScreen.Options)
      val budget = terminal.rows - PaneLayout.runtimeTailRows
      val tooTall = if (panel.size <= budget) None else Some(s"$terminal panel=${panel.size} budget=$budget")
      tooTall ++ panel.collect { case line if line.length > terminal.columns => s"$terminal |$line|" }
    }
    assertEquals(offenders, Seq.empty[String])
  }

  test("the confirmation dialog stays inside the terminal") {
    val offenders = sizes.flatMap { terminal =>
      val dialog = renderAt(terminal = terminal, screen = TuiScreen.Confirm, index = indexWithLongNames)
      val budget = terminal.rows - PaneLayout.runtimeTailRows
      val tooTall = if (dialog.size <= budget) None else Some(s"$terminal dialog=${dialog.size} budget=$budget")
      tooTall ++ dialog.collect { case line if line.length > terminal.columns => s"$terminal |$line|" }
    }
    assertEquals(offenders, Seq.empty[String])
  }

  test("the confirmation dialog names the definition it is about to slice") {
    val dialog =
      renderAt(
        terminal = TerminalSize(rows = 24, columns = 100),
        screen = TuiScreen.Confirm,
        index = indexWithMixedNames
      )
    assert(dialog.exists(_.contains("com.example")), dialog.mkString("\n"))
  }

  test("the options panel explains the option under the cursor") {
    val terminal = TerminalSize(rows = 40, columns = 100)
    val panels = SliceOptionLabel.values.toVector.map { switch =>
      renderAt(terminal = terminal, screen = TuiScreen.Options, optionCursor = switch)
    }
    val tooShort = panels.filter(_.size <= SliceOptionLabel.values.length + boxRows)
    assertEquals(tooShort, Vector.empty[Vector[String]])
    assertEquals(panels.distinct.size, panels.size)
  }

  test("panes stretch and squash with the terminal height") {
    val heights = Seq(14, 24, 40, 70).map(rows => renderAt(terminal = TerminalSize(rows = rows, columns = 100)).size)
    assertEquals(heights.sorted, heights)
    assert(heights.last - heights.head > 40, heights.toString)
  }
}
