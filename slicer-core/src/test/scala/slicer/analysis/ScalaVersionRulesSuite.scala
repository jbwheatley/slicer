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

import scala.meta.*
import scala.meta.internal.semanticdb

import slicer.analysis.ScalaVersionRules.*
import slicer.harness.{TestProject, TestProject213}
import slicer.model.Symbol

class ScalaVersionRulesSuite extends munit.FunSuite {

  test("the Scala version in the SemanticDB output path picks the dialect the CLI parses with") {
    assertEquals(ScalaVersionRules.rulesForSemanticdbDirs(TestProject213.semanticdbDirs), Scala213Rules)
    assertEquals(ScalaVersionRules.rulesForSemanticdbDirs(TestProject.semanticdbDirs), Scala3Rules)
    assertEquals(ScalaVersionRules.rulesForSemanticdbDirs(Vector(Paths.get("/tmp/nothing"))), Scala3Rules)
  }

  test("SemanticDB without synthetics is called out, because implicits are invisible in it") {
    val bare = semanticdb.TextDocument(uri = "Bare.scala")
    assert(Scala213Rules.checkForMissingSynthetics(Vector(bare)).exists(_.contains("-P:semanticdb:synthetics:on")))
    assertEquals(Scala213Rules.checkForMissingSynthetics(Vector.empty), None)
    assertEquals(Scala3Rules.checkForMissingSynthetics(Vector(bare)), None)
  }

  private val conversionSource =
    """object Conversions {
      |  implicit def convertsOne(value: String): Int = value.length
      |  implicit def convertsThenTakesMore(value: String)(other: Int): Int = value.length + other
      |  implicit def takesTwo(value: String, other: Int): Int = value.length + other
      |  implicit def takesImplicitOnly(implicit value: String): Int = value.length
      |  implicit def takesNothing: Int = 0
      |  implicit class Wrapper(value: String) { def shouted: String = value.toUpperCase }
      |  def plain(value: String): Int = value.length
      |  class PlainClass(value: String)
      |}
      |""".stripMargin

  private val conversionTree: Tree = dialects.Scala213(conversionSource).parse[Source].get

  private val symbolAtStart: Map[Int, Symbol] =
    conversionTree.collect {
      case d: Defn.Def   => d.pos.start -> Symbol(s"conversions/${d.name.value}().")
      case d: Defn.Class => d.pos.start -> Symbol(s"conversions/${d.name.value}#")
    }.toMap

  private def collectedNames: Vector[String] =
    Scala213Rules.collectConversions(conversionTree, symbolAtStart).map(_.value.stripPrefix("conversions/"))

  test("an implicit method converting a single explicit argument is a conversion") {
    assert(collectedNames.contains("convertsOne()."), collectedNames.toString)
    assert(collectedNames.contains("convertsThenTakesMore()."), collectedNames.toString)
  }

  test("an implicit class is a conversion, a plain class is not") {
    assert(collectedNames.contains("Wrapper#"), collectedNames.toString)
    assert(!collectedNames.contains("PlainClass#"), collectedNames.toString)
  }

  test("an implicit method that converts nothing is left alone") {
    assert(!collectedNames.contains("takesTwo()."), collectedNames.toString)
    assert(!collectedNames.contains("takesImplicitOnly()."), collectedNames.toString)
    assert(!collectedNames.contains("takesNothing()."), collectedNames.toString)
  }

  test("a method that is not implicit is never a conversion") {
    assert(!collectedNames.contains("plain()."), collectedNames.toString)
  }

  test("a conversion whose definition carries no symbol is dropped rather than guessed at") {
    assertEquals(Scala213Rules.collectConversions(conversionTree, Map.empty), Vector.empty)
  }

  test("Scala 3 rules read implicit conversions as well as given ones") {
    val scala3Names =
      Scala3Rules.collectConversions(conversionTree, symbolAtStart).map(_.value.stripPrefix("conversions/"))
    assert(scala3Names.contains("convertsOne()."), scala3Names.toString)
    assert(scala3Names.contains("Wrapper#"), scala3Names.toString)
  }
}
