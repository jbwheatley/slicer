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

import slicer.model.{DefKind, DefNode, SliceOptions, Symbol}
import slicer.tui.SampleIndexes

import cats.syntax.eq.*

class CandidateFilterSuite extends munit.FunSuite {

  private def node(symbol: String, kind: DefKind, owner: Option[String], isAbstract: Boolean): DefNode =
    DefNode(
      symbol = Symbol(symbol),
      kind = kind,
      displayName = symbol.split("[./#]").filter(_.nonEmpty).last.takeWhile(_ =!= '('),
      file = Paths.get("Fake.scala"),
      start = 0,
      end = 1,
      owner = owner.map(Symbol.apply),
      isAbstract = isAbstract,
      expandsAtCallSite = false
    )

  private def symbolRelation(relation: Map[String, Set[String]]): Map[Symbol, Set[Symbol]] =
    relation.map { case (from, to) => Symbol(from) -> to.map(Symbol.apply) }

  private val followingImplementations = SliceOptions(followImplementations = true, keepFields = false)

  private val stoppingAtAbstract = SliceOptions(followImplementations = false, keepFields = false)

  private val trait_ = node(symbol = "spec/Repo#", kind = DefKind.Trait, owner = None, isAbstract = false)

  private val abstractMember =
    node(symbol = "spec/Repo#find().", kind = DefKind.Def, owner = Some("spec/Repo#"), isAbstract = true)

  private val concreteMember =
    node(symbol = "spec/Repo#findAll().", kind = DefKind.Def, owner = Some("spec/Repo#"), isAbstract = false)

  private val implementation =
    node(symbol = "spec/DbRepo#find().", kind = DefKind.Def, owner = Some("spec/DbRepo#"), isAbstract = false)

  private def selectSliceCandidates(
      nodes: Vector[DefNode],
      overriddenBy: Map[String, Set[String]],
      options: SliceOptions
  ) =
    CandidateFilter
      .selectSliceCandidates(SampleIndexes.containing(nodes, symbolRelation(overriddenBy)), options)
      .map(_.symbol)
      .toSet

  test("parameters, enum cases, type members, enums and extension groups are never offered") {
    val skipped = Vector(
      node(symbol = "spec/A#p.", kind = DefKind.Param, owner = Some("spec/A#"), isAbstract = false),
      node(symbol = "spec/Colour.Red.", kind = DefKind.EnumCase, owner = Some("spec/Colour."), isAbstract = false),
      node(symbol = "spec/A#T#", kind = DefKind.Type, owner = Some("spec/A#"), isAbstract = false),
      node(symbol = "spec/Colour#", kind = DefKind.Enum, owner = None, isAbstract = false),
      node(symbol = "extension-group:A.scala:0", kind = DefKind.Extension, owner = None, isAbstract = false)
    )
    assertEquals(
      selectSliceCandidates(nodes = skipped, overriddenBy = Map.empty, options = followingImplementations),
      Set.empty[Symbol]
    )
  }

  test("a Java type is reached by a slice, never offered as its root") {
    val javaType = node(symbol = "spec/Registry#", kind = DefKind.JavaType, owner = None, isAbstract = false)
    assertEquals(
      selectSliceCandidates(nodes = Vector(javaType), overriddenBy = Map.empty, options = followingImplementations),
      Set.empty[Symbol]
    )
  }

  test("a container is offered only once it has methods of its own") {
    val bare = node(symbol = "spec/Empty.", kind = DefKind.Object, owner = None, isAbstract = false)
    val method = node(symbol = "spec/Full.run().", kind = DefKind.Def, owner = Some("spec/Full."), isAbstract = false)
    val full = node(symbol = "spec/Full.", kind = DefKind.Object, owner = None, isAbstract = false)

    assertEquals(
      selectSliceCandidates(
        nodes = Vector(bare, full, method),
        overriddenBy = Map.empty,
        options = followingImplementations
      ),
      Set(full.symbol, method.symbol)
    )
  }

  test("a trait is offered while implementations are followed only if something implements it") {
    val unimplemented =
      selectSliceCandidates(
        nodes = Vector(trait_, abstractMember),
        overriddenBy = Map.empty,
        options = followingImplementations
      )
    assert(!unimplemented.contains(trait_.symbol), unimplemented.toString)

    val implemented = selectSliceCandidates(
      nodes = Vector(trait_, abstractMember, implementation),
      overriddenBy = Map(abstractMember.symbol.value -> Set(implementation.symbol.value)),
      options = followingImplementations
    )
    assert(implemented.contains(trait_.symbol), implemented.toString)
  }

  test("a trait is offered while implementations are not followed only if it can run on its own") {
    val abstractOnly =
      selectSliceCandidates(
        nodes = Vector(trait_, abstractMember),
        overriddenBy = Map.empty,
        options = stoppingAtAbstract
      )
    assert(!abstractOnly.contains(trait_.symbol), abstractOnly.toString)

    val withConcrete = selectSliceCandidates(
      nodes = Vector(trait_, abstractMember, concreteMember),
      overriddenBy = Map.empty,
      options = stoppingAtAbstract
    )
    assert(withConcrete.contains(trait_.symbol), withConcrete.toString)
  }

  test("a method, val or given is always offered") {
    val members = Vector(
      concreteMember,
      node(symbol = "spec/A#count.", kind = DefKind.Val, owner = Some("spec/A#"), isAbstract = false),
      node(symbol = "spec/A#given_Eq.", kind = DefKind.Given, owner = Some("spec/A#"), isAbstract = false)
    )
    assertEquals(
      selectSliceCandidates(nodes = members, overriddenBy = Map.empty, options = followingImplementations),
      members.map(_.symbol).toSet
    )
  }

  test("the group a multi-name definition is indexed under is not offered, the names it binds are") {
    val group = DefNode(
      symbol = Symbol.synthetic("binding-group:Fake.scala:0"),
      kind = DefKind.Val,
      displayName = "firstOfTwo",
      file = Paths.get("Fake.scala"),
      start = 0,
      end = 10,
      owner = Some(Symbol("spec/Holder.")),
      isAbstract = false,
      expandsAtCallSite = false
    )
    val bound = node(
      symbol = "spec/Holder.firstOfTwo.",
      kind = DefKind.Binding,
      owner = Some(group.symbol.value),
      isAbstract = false
    )

    assertEquals(
      selectSliceCandidates(nodes = Vector(group, bound), overriddenBy = Map.empty, options = followingImplementations),
      Set(bound.symbol)
    )
  }

}
