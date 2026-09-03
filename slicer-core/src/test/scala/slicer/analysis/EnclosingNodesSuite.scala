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

package slicer.analysis

import java.nio.file.Paths

import slicer.model.{DefKind, DefNode, Symbol}

class EnclosingNodesSuite extends munit.FunSuite {

  private val file = Paths.get("/project/src/main/scala/spec/Sample.scala")

  private def nodeOf(name: String, start: Int, end: Int, kind: DefKind): DefNode =
    DefNode(
      symbol = Symbol(s"spec/Sample#$name()."),
      kind = kind,
      displayName = name,
      file = file,
      start = start,
      end = end,
      owner = Some(Symbol("spec/Sample#")),
      isAbstract = false,
      expandsAtCallSite = false
    )

  private val outer = nodeOf(name = "outer", start = 0, end = 100, kind = DefKind.Class)
  private val inner = nodeOf(name = "inner", start = 20, end = 60, kind = DefKind.Def)
  private val innermost = nodeOf(name = "innermost", start = 30, end = 40, kind = DefKind.Def)

  private val nodes = EnclosingNodes(Vector(outer, inner, innermost))

  private val referenced = Symbol("spec/Other#used().")

  private def ownersOf(offsets: Vector[Int]): Vector[Symbol] =
    nodes.attributeToOwners(offsets.map(_ -> referenced)).map(_._1)

  test("an offset lands in the smallest definition that spans it") {
    assertEquals(ownersOf(Vector(35, 50, 90)), Vector(innermost.symbol, inner.symbol, outer.symbol))
  }

  test("a definition ends before its end offset, and starts on its start offset") {
    assertEquals(ownersOf(Vector(30, 40, 100)), Vector(innermost.symbol, inner.symbol))
  }

  test("an offset outside every definition encloses nothing") {
    assertEquals(ownersOf(Vector(-1, 400)), Vector.empty)
  }

  test("references are attributed to the owner they sit in, and one that sits nowhere is dropped") {
    assertEquals(
      nodes.attributeToOwners(Vector(35 -> referenced, 50 -> referenced, 400 -> referenced)),
      Vector(innermost.symbol -> referenced, inner.symbol -> referenced)
    )
  }

  test("owners come back in the order they were asked for, whatever order the offsets arrive in") {
    assertEquals(ownersOf(Vector(90, 35, 50)), Vector(outer.symbol, innermost.symbol, inner.symbol))
  }

  test("an offset back inside a definition already left is attributed to it again") {
    assertEquals(ownersOf(Vector(35, 90, 35)), Vector(innermost.symbol, outer.symbol, innermost.symbol))
  }
}
