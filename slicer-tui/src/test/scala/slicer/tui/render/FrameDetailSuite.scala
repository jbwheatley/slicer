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

class FrameDetailSuite extends munit.FunSuite {

  test("a level shows everything at or below it, and nothing above it") {
    assert(FrameDetail.Full.showsAtLeast(FrameDetail.Minimal))
    assert(FrameDetail.Full.showsAtLeast(FrameDetail.Full))
    assert(FrameDetail.Compact.showsAtLeast(FrameDetail.Bare))
    assert(!FrameDetail.Minimal.showsAtLeast(FrameDetail.Compact))
    assert(!FrameDetail.Bare.showsAtLeast(FrameDetail.Minimal))
  }

  test("levels are ordered from the barest to the richest") {
    assertEquals(FrameDetail.values.toVector, Vector.from(FrameDetail.richestFirst.reverse))
    assertEquals(FrameDetail.richestFirst.head, FrameDetail.Full)
    assertEquals(FrameDetail.richestFirst.last, FrameDetail.Bare)
  }
}
