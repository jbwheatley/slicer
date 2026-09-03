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

import tui.viewport.TerminalEscapes

private[render] object TextFit {

  def padToWidth(line: String, width: Int): String =
    if (line.length <= width) line.padTo(width, ' ') else line.take(math.max(width - 1, 0)) + "…"

  def truncateKeepingTail(line: String, width: Int): String =
    if (line.length <= width) line else "…" + line.takeRight(math.max(width - 1, 0))

  def stripStyling(line: String): String = line.replaceAll(TerminalEscapes.stylingPattern, "")
}
