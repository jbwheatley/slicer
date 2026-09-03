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

import slicer.model.SliceOptions

import cats.Eq

private[tui] enum SliceOptionLabel(val label: String, val detail: String) {

  case FollowImplementations
      extends SliceOptionLabel(
        label = "follow abstract types into every implementation of them",
        detail = "Adds every implementation of abstract classes/traits the slice uses. The implementation can be " +
          "injected above the definition you asked for, so the slice cannot tell which one is used."
      )

  case KeepFields
      extends SliceOptionLabel(
        label = "keep all fields of a kept class even when nothing reads them",
        detail =
          "A class may include some initialisation in a top level expression or val/var, which can be an implementation detail."
      )

  def isEnabled(options: SliceOptions): Boolean = this match {
    case FollowImplementations => options.followImplementations
    case KeepFields            => options.keepFields
  }

  def applyToOptions(options: SliceOptions, enabled: Boolean): SliceOptions = this match {
    case FollowImplementations => options.copy(followImplementations = enabled)
    case KeepFields            => options.copy(keepFields = enabled)
  }
}

private[tui] object SliceOptionLabel {
  given Eq[SliceOptionLabel] = Eq.fromUniversalEquals
}
