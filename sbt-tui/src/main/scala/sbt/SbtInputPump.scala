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

package sbt

import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import scala.util.Try

import sbt.internal.util.Terminal as Console

private[sbt] class SbtInputPump {

  private val listening: AtomicReference[Option[BlockingQueue[Integer]]] = AtomicReference(None)

  private val reading: AtomicBoolean = AtomicBoolean(false)

  private val endOfInput: Int = -1

  def forwardInputTo(console: Console, typed: BlockingQueue[Integer]): Unit = {
    listening.set(Some(typed))
    startUnlessReading(console)
  }

  def stopForwarding(): Unit = listening.set(None)

  private def startUnlessReading(console: Console): Unit =
    if (reading.compareAndSet(false, true)) {
      val pump = Thread(() => routeUntilUnwanted(console), "slice-terminal-input")
      pump.setDaemon(true)
      pump.start()
    }

  private def routeUntilUnwanted(console: Console): Unit = {
    val unwanted = Iterator
      .continually(Try(console.inputStream.read()).getOrElse(endOfInput))
      .takeWhile(_ >= 0)
      .find(byte =>
        listening.get() match {
          case Some(typed) =>
            typed.put(byte)
            false
          case None => true
        }
      )

    reading.set(false)

    unwanted match {
      case Some(byte) =>
        handBackToConsole(console, byte)
        if (listening.get().isDefined) startUnlessReading(console)
      case None => ()
    }
  }

  private def handBackToConsole(console: Console, byte: Int): Unit = console.inputStream match {
    case sbtStream: Console.WriteableInputStream => sbtStream.write(byte)
    case _                                       => ()
  }
}
