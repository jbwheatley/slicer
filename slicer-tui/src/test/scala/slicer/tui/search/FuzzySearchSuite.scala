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

package slicer.tui.search

import java.nio.file.Paths

import slicer.model.{DefKind, DefNode, Symbol}

import cats.syntax.eq.*

// scalafix:off DisableSyntax.defaultArgs
class FuzzySearchSuite extends munit.FunSuite {

  private def node(symbol: String, kind: DefKind = DefKind.Def): DefNode =
    DefNode(
      symbol = Symbol(symbol),
      kind = kind,
      displayName = symbol.split("[./#]").filter(_.nonEmpty).last.takeWhile(_ =!= '('),
      file = Paths.get("Fake.scala"),
      start = 0,
      end = 1,
      owner = None,
      isAbstract = false,
      expandsAtCallSite = false
    )

  private val candidates = Vector(
    node("com/example/util/Text.normalize()."),
    node("com/example/util/Codec.encode()."),
    node("com/example/service/Orders.normalizeAll()."),
    node("com/example/repo/Db.find()."),
    node("com/example/repo/CachingUserRepo#getUser().")
  )

  private def prettyNames(query: String): Vector[String] =
    FuzzySearch.rankCandidates(query, candidates).map(_.dottedName)

  test("empty query returns every candidate, shortest first") {
    val ranked = prettyNames("")
    assertEquals(ranked.size, candidates.size)
    assertEquals(ranked.head, "com.example.repo.Db.find")
  }

  test("a qualified name is searched whole, not only its last segment") {
    assertEquals(prettyNames("com.example.util.Text.normalize").head, "com.example.util.Text.normalize")
    assertEquals(prettyNames("example.repo.Db.find").head, "com.example.repo.Db.find")
  }

  test("an exact name outranks a longer name that starts with the query") {
    assertEquals(prettyNames("normalize").head, "com.example.util.Text.normalize")
  }

  test("non-matching query returns nothing") {
    assertEquals(prettyNames("zzzz"), Vector.empty)
  }

  test("matching is case insensitive and skips gaps inside the name") {
    assertEquals(prettyNames("nAll"), Vector("com.example.service.Orders.normalizeAll"))
  }

  test("the package path is not searched") {
    assertEquals(prettyNames("example"), Vector.empty)
    assertEquals(prettyNames("codec"), Vector.empty)
  }

  test("a subsequence scattered inside one word does not match") {
    assertEquals(prettyNames("nlz"), Vector.empty)
    assertEquals(prettyNames("cdc"), Vector.empty)
  }

  test("a contiguous run matches wherever it starts") {
    assertEquals(prettyNames("ser"), Vector("com.example.repo.CachingUserRepo.getUser"))
  }

  test("a match on word starts outranks one starting mid-word") {
    assertEquals(prettyNames("gu").head, "com.example.repo.CachingUserRepo.getUser")
  }

  test("a transposed query still finds the name") {
    assertEquals(prettyNames("nomralize").head, "com.example.util.Text.normalize")
    assertEquals(prettyNames("fnid"), Vector("com.example.repo.Db.find"))
  }

  test("an exact match outranks a near miss of the same name") {
    assertEquals(prettyNames("encode").head, "com.example.util.Codec.encode")
  }

  test("a query under four characters gets no typo tolerance") {
    assertEquals(prettyNames("fnd"), Vector.empty)
  }

  test("two typos still find the name, ranked by the letters they share") {
    assertEquals(prettyNames("nomralzie").head, "com.example.util.Text.normalize")
  }

  private val mixedKinds = Vector(
    node("com/example/repo/UserRepo#", kind = DefKind.Trait),
    node("com/example/repo/DbUserRepo#", kind = DefKind.Class),
    node("com/example/repo/DbUserRepo#find().", kind = DefKind.Def),
    node("com/example/repo/Repos.", kind = DefKind.Object),
    node("com/example/repo/DbUserRepo#cache.", kind = DefKind.Val),
    node("com/example/repo/DbUserRepo#validate().", kind = DefKind.Def)
  )

  private def mixedNames(query: String): Vector[String] =
    FuzzySearch.rankCandidates(query, mixedKinds).map(_.dottedName)

  test("a kind keyword restricts matches to that kind") {
    assertEquals(mixedNames("def find"), Vector("com.example.repo.DbUserRepo.find"))
    assertEquals(mixedNames("val cache"), Vector("com.example.repo.DbUserRepo.cache"))
  }

  test("a kind keyword with nothing after it lists every definition of that kind") {
    assertEquals(mixedNames("class "), Vector("com.example.repo.DbUserRepo"))
    assertEquals(mixedNames("trait "), Vector("com.example.repo.UserRepo"))
  }

  test("a kind keyword with no trailing space stays an ordinary needle") {
    assertEquals(mixedNames("val"), Vector("com.example.repo.DbUserRepo.validate"))
  }

  test("a word that is not a kind keyword stays an ordinary needle") {
    assertEquals(mixedNames("enum repo"), Vector.empty)
  }

  test("a whole word of the name outranks a name that merely starts with the query") {
    assertEquals(mixedNames("repo").head, "com.example.repo.UserRepo")
  }
}
