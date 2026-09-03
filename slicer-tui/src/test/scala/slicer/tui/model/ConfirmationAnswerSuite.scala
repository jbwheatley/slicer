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

package slicer.tui.model

class ConfirmationAnswerSuite extends munit.FunSuite {

  test("the confirmation offers yes before no") {
    assertEquals(ConfirmationAnswer.all, Vector(ConfirmationAnswer.Yes, ConfirmationAnswer.No))
  }

  test("moving past either end of the answers wraps around to the other") {
    assertEquals(ConfirmationAnswer.answerAtOrdinal(0), ConfirmationAnswer.Yes)
    assertEquals(ConfirmationAnswer.answerAtOrdinal(1), ConfirmationAnswer.No)
    assertEquals(ConfirmationAnswer.answerAtOrdinal(2), ConfirmationAnswer.Yes)
    assertEquals(ConfirmationAnswer.answerAtOrdinal(-1), ConfirmationAnswer.No)
    assertEquals(ConfirmationAnswer.answerAtOrdinal(-2), ConfirmationAnswer.Yes)
  }
}
