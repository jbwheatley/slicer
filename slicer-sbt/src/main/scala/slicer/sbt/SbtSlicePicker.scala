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

import slicer.model.SliceFailure
import slicer.tui.{SlicePicker, SliceTui}

import _root_.sbt.{SbtLayoutzApp, SbtTerminalSizeTracking}
import cats.syntax.either.*

private[slicer] object SbtSlicePicker extends SlicePicker(SbtTerminalSizeTracking, tickIntervalMs = 250L) {

  override protected def openPickerImpl(tui: SliceTui): Either[SliceFailure, Unit] =
    Either.catchNonFatal(SbtLayoutzApp.create(tui)) match {
      case Left(err) => Left(SliceFailure(err.getMessage))
      case Right(console) =>
        Right(
          tui.reportingOnShutdown(
            console.run(
              tickIntervalMs = tui.tickIntervalMs,
              renderIntervalMs = SliceTui.repaintIntervalMs,
              quitMessage = SliceTui.quitMessage,
              showQuitMessage = true
            )
          )
        )
    }
}
