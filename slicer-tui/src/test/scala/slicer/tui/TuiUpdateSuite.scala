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

import slicer.model.{BuildTool, Platform, SliceOptions}
import slicer.tui.model.{TuiMessage, TuiState}

import layoutz.Cmd
import tui.viewport.{TerminalSize, TerminalSizeTracking}

class TuiUpdateSuite extends munit.FunSuite {

  private val terminal = TerminalSize(rows = 24, columns = 100)

  private val tui = SliceTui(
    index = SampleIndexes.withLongSource,
    sourceRoot = Paths.get("/home/someone/project"),
    out = Paths.get("/home/someone/project/target/slices"),
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

  private val state: TuiState = tui.init._1.copy(terminal = terminal)

  test("a resize wipes the screen the old frame was drawn on") {
    val wider = TerminalSize(rows = 24, columns = 140)
    val (resized, command) = tui.update(TuiMessage.SetTerminalSize(wider), state)
    assertEquals(resized.terminal, wider)
    assertNotEquals(command, Cmd.none: Cmd[TuiMessage])
  }

  test("an unchanged state is drawn once and handed back, not rebuilt every tick") {
    val drawn = tui.view(state)
    assert(drawn eq tui.view(state), "the frame was rebuilt for an unchanged state")
  }

  test("a changed state draws a fresh frame") {
    val typed = state.typeCharacter('o', Vector.empty)
    assertNotEquals(tui.view(state).render, tui.view(typed).render)
  }

  test("a resize to the same size leaves the screen alone") {
    val (unchanged, command) = tui.update(TuiMessage.SetTerminalSize(terminal), state)
    assertEquals(unchanged, state)
    assertEquals(command, Cmd.none: Cmd[TuiMessage])
  }
}
