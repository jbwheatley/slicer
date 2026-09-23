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

package slicer.sbt

class OpenSlicePickerSuite extends munit.FunSuite {

  test("a JDK older than the picker's is refused before the picker is forked") {
    assert(OpenSlicePicker.findJavaTooOldForPicker("1.8").isDefined)
    assert(OpenSlicePicker.findJavaTooOldForPicker("11").isDefined)
  }

  test("a JDK the picker runs on is let through") {
    assertEquals(obtained = OpenSlicePicker.findJavaTooOldForPicker("17"), expected = None)
    assertEquals(obtained = OpenSlicePicker.findJavaTooOldForPicker("25"), expected = None)
  }
}
