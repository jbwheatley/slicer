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

import cats.Eq
import cats.Order

private[tui] enum FrameDetail {
  case Bare, Minimal, Compact, Full

  def showsAtLeast(wanted: FrameDetail): Boolean = ordinal >= wanted.ordinal
}

private[tui] object FrameDetail {

  given Eq[FrameDetail] = Eq.fromUniversalEquals

  given Order[FrameDetail] = Order.by(_.ordinal)

  val richestFirst: Vector[FrameDetail] = values.toVector.reverse
}
