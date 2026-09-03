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

package slicer.emit

import slicer.analysis.ScalaVersionRules
import slicer.harness.TestProject213

import cats.syntax.eq.*

class Scala213EmitSuite extends munit.FunSuite {

  test("a wildcard import of a dropped object is dropped with it") {
    val handler = TestProject213.slice("spec.entry.Handler.handlesWithOneParameter").file("entry/Handler.scala")
    assert(!handler.contains("import spec.implicitclasses"), handler)
  }

  test("a wildcard import of a kept object survives") {
    val handler = TestProject213.slice("spec.entry.Handler.handlesWithImplicitClass").file("entry/Handler.scala")
    assert(handler.contains("import spec.implicitclasses.StringSyntax._"), handler)
  }

  test("the generated build names the Scala 2 version the corpus was compiled with") {
    assert(TestProject213.scalaVersion.startsWith("2.13"), TestProject213.scalaVersion)
    assertEquals(TestProject213.language, ScalaVersionRules.Scala213Rules)
  }

  test("a block comment above a dropped definition goes with it, whole") {
    val comments = TestProject213.slice("spec.comments.CallsBlockCommented.calls").file("comments/Comments.scala")
    assert(comments.contains("Kept definition documented across lines"), comments)
    assert(!comments.contains("Dropped definition documented across lines"), comments)
    assert(!comments.contains("Dropped definition with an aligned block comment"), comments)
    assertEquals(countOf("/*", comments), countOf("*/", comments), comments)
  }

  private def countOf(token: String, text: String): Int = text.sliding(token.length).count(_ === token)
}
