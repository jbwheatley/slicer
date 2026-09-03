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

import slicer.harness.TestProject
import slicer.model.{DefKind, Symbol}

import cats.syntax.eq.*

class IndexSuite extends munit.FunSuite {

  private val index = TestProject.index

  test("every module of the test project is indexed") {
    val packages = index.defs.values.map(d => d.symbol.toPackagePrefix).toSet
    Vector("spec/members/", "spec/implementations/", "spec/external/", "spec/services/", "spec/entry/")
      .foreach(p => assert(packages.contains(p), s"$p missing from ${packages.toVector.sorted.mkString(", ")}"))
  }

  test("a definition resolves by pretty name, by suffix and by raw symbol") {
    val expected = Symbol("spec/entry/Handler#handlesWithOneParameter().")
    assertEquals(TestProject.symbolOf("spec.entry.Handler.handlesWithOneParameter"), expected)
    assertEquals(TestProject.symbolOf("Handler.handlesWithOneParameter"), expected)
    assertEquals(TestProject.symbolOf(expected.value), expected)
  }

  test("constructor parameters and extension groups are indexed as definitions of their own") {
    assert(index.defs.values.exists(d => d.kind === DefKind.Param && d.displayName === "other"))
    assert(index.defs.values.exists(d => d.kind === DefKind.Extension && d.symbol.isSynthetic))
  }

  test("an anonymous given is indexed under the symbol its own members are owned by") {
    val instance = index.defs.get(Symbol("spec/external/HasLibraryGiven.given_Semigroup_HasLibraryGiven."))
    assertEquals(instance.map(_.kind), Some(DefKind.Given))
    assertEquals(instance.flatMap(_.owner), Some(Symbol("spec/external/HasLibraryGiven.")))
    val combine = index.defs(Symbol("spec/external/HasLibraryGiven.given_Semigroup_HasLibraryGiven.combine()."))
    assertEquals(combine.owner, Some(Symbol("spec/external/HasLibraryGiven.given_Semigroup_HasLibraryGiven.")))
  }

  test("references become edges from the innermost enclosing definition") {
    val edges =
      index.edges
        .getOrElse(TestProject.symbolOf("spec.entry.Handler.handlesWithOneParameter"), Set.empty)
        .map(_.toDottedName)
    assert(edges.contains("spec.services.FirstService.reachedFromOneCaller"), edges.toVector.sorted.mkString(", "))
    assert(!edges.contains("spec.services.FirstService.reachedFromAnotherCaller"))
  }

  test("overrides are recorded in both directions") {
    val abstractMember = TestProject.symbolOf("spec.implementations.AbstractWithTwoMembers.calledMember")
    assert(
      index.overriddenBy
        .getOrElse(abstractMember, Set.empty)
        .map(_.toDottedName)
        .contains("spec.implementations.DirectImplementation.calledMember")
    )
    val implementation = TestProject.symbolOf("spec.implementations.DirectImplementation.calledMember")
    assert(index.overrides.getOrElse(implementation, Set.empty).contains(abstractMember))
  }

  test("`derives` is recorded even though it emits no occurrence") {
    val derived = TestProject.symbolOf("spec.derivation.DerivesTypeClass")
    assertEquals(index.derivations.getOrElse(derived, Set.empty), Set("DerivableTypeClass"))
  }

  test("an inline implementation is recorded as an instantiation, a named one is not") {
    val instantiated = index.instantiations.values.flatten.map(_.toDottedName).toSet
    assert(instantiated.contains("spec.givens.ContextParameter"), instantiated.toVector.sorted.mkString(", "))
  }

  test("every name a definition binds is indexed, whatever the pattern binding it") {
    Vector(
      "spec.bindings.BoundNames.firstOfTwo",
      "spec.bindings.BoundNames.secondOfTwo",
      "spec.bindings.BoundNames.leftOfPair",
      "spec.bindings.BoundNames.rightOfPair",
      "spec.bindings.BoundNames.unwrapped",
      "spec.bindings.BoundNames.secondMutable"
    ).foreach(query => assertEquals(TestProject.index.defs(TestProject.symbolOf(query)).kind, DefKind.Binding))
  }

  test("a name bound beside others is owned by the definition binding it, not by the enclosing object") {
    val bound = TestProject.index.defs(TestProject.symbolOf("spec.bindings.BoundNames.secondOfTwo"))
    val group = bound.owner.flatMap(TestProject.index.defs.get).getOrElse(fail("the binding has no owner"))
    assert(group.symbol.isSynthetic, group.toString)
    assertEquals(group.kind, DefKind.Val)
    assertEquals(group.owner.map(_.value), Some("spec/bindings/BoundNames."))
  }

  test("an enum case declared beside others is indexed") {
    Vector("spec.bindings.Colour.Red", "spec.bindings.Colour.Green")
      .foreach(query => assertEquals(TestProject.index.defs(TestProject.symbolOf(query)).kind, DefKind.Binding))
  }

  test("a declared var and a secondary constructor are indexed") {
    assertEquals(
      TestProject.index.defs(TestProject.symbolOf("spec.bindings.DeclaresMutableMember.declaredVar")).kind,
      DefKind.Var
    )
    val constructors =
      TestProject.index.defs.values.filter(node =>
        node.dottedName.startsWith("spec.bindings.TakesTwoParameters") && node.symbol.isConstructorSymbol
      )
    assertEquals(constructors.size, 1)
  }

}
