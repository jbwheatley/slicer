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

import slicer.harness.{Corpus, TestProject213, TestProjectMill213}
import slicer.model.{DefKind, Symbol}

import cats.syntax.eq.*

class Scala213IndexSuite extends munit.FunSuite {

  private val corpuses: Vector[(String, Corpus)] =
    Vector("sbt" -> TestProject213, "mill" -> TestProjectMill213)

  corpuses.foreach { case (tool, corpus) =>

    test(s"every module of the Scala 2 corpus built by $tool is indexed") {
      val packages = corpus.index.defs.values.map(d => d.symbol.toPackagePrefix).toSet
      Vector("spec/implicits/", "spec/implicitclasses/", "spec/external/", "spec/services/", "spec/entry/")
        .foreach(p => assert(packages.contains(p), s"$p missing from ${packages.toVector.sorted.mkString(", ")}"))
    }

    test(s"a definition of the corpus built by $tool resolves by pretty name, by suffix and by raw symbol") {
      val expected = Symbol("spec/entry/Handler#handlesWithOneParameter().")
      assertEquals(corpus.symbolOf("spec.entry.Handler.handlesWithOneParameter"), expected)
      assertEquals(corpus.symbolOf("Handler.handlesWithOneParameter"), expected)
      assertEquals(corpus.symbolOf(expected.value), expected)
    }

    test(s"an implicit class of the corpus built by $tool is indexed with the members it adds") {
      val syntax = corpus.symbolOf("spec.implicitclasses.StringSyntax.ImplicitClassOnString")
      val shouted =
        corpus.index.defs(corpus.symbolOf("spec.implicitclasses.StringSyntax.ImplicitClassOnString.shouted"))
      assertEquals(shouted.owner, Some(syntax))
    }

    test(s"a member of a package object of the corpus built by $tool is owned by it") {
      val member = corpus.index.defs(corpus.symbolOf("spec.legacy.package.readsAlias"))
      assertEquals(member.owner.map(_.value), Some("spec/legacy/package."))
    }

    test(s"references of the corpus built by $tool become edges from the innermost enclosing definition") {
      val edges =
        corpus.index.edges
          .getOrElse(corpus.symbolOf("spec.entry.Handler.handlesWithOneParameter"), Set.empty)
          .map(_.toDottedName)
      assert(edges.contains("spec.services.FirstService.reachedFromOneCaller"), edges.toVector.sorted.mkString(", "))
      assert(!edges.contains("spec.services.FirstService.reachedFromAnotherCaller"))
    }

    test(s"overrides of the corpus built by $tool are recorded in both directions") {
      val abstractMember = corpus.symbolOf("spec.implementations.AbstractWithTwoMembers.calledMember")
      assert(
        corpus.index.overriddenBy
          .getOrElse(abstractMember, Set.empty)
          .map(_.toDottedName)
          .contains("spec.implementations.DirectImplementation.calledMember")
      )
      val implementation = corpus.symbolOf("spec.implementations.DirectImplementation.calledMember")
      assert(corpus.index.overrides.getOrElse(implementation, Set.empty).contains(abstractMember))
    }

    test(s"the synthetics of the corpus built by $tool make an implicit class call an edge of its own") {
      val edges =
        corpus.index.edges
          .getOrElse(corpus.symbolOf("spec.entry.Handler.handlesWithImplicitClass"), Set.empty)
          .map(_.toDottedName)
      assert(
        edges.contains("spec.implicitclasses.StringSyntax.ImplicitClassOnString.shouted"),
        edges.toVector.sorted.mkString(", ")
      )
    }

    test(s"the synthetics of the corpus built by $tool make an implicit argument an edge of its own") {
      val edges =
        corpus.index.edges
          .getOrElse(corpus.symbolOf("spec.implicits.CallsTypeClass.rendersPair"), Set.empty)
          .map(_.toDottedName)
      assert(
        edges.contains("spec.implicits.TypeClass.typeClassForPair"),
        edges.toVector.sorted.mkString(", ")
      )
    }

    test(s"an anonymous implicit implementation of the corpus built by $tool is recorded as an instantiation") {
      val instantiated = corpus.index.instantiations.values.flatten.map(_.toDottedName).toSet
      assert(instantiated.contains("spec.implicits.TypeClass"), instantiated.toVector.sorted.mkString(", "))
    }

    test(s"constructor parameters of the corpus built by $tool are indexed as definitions of their own") {
      assert(
        corpus.index.defs.values.exists(d => d.kind === DefKind.Param && d.displayName === "first"),
        "no constructor parameter was indexed"
      )
    }
  }

  test("a Scala 2 macro definition is indexed as a def that expands at its call site") {
    val macroDefinition = TestProject213.index.defs(TestProject213.symbolOf("spec.macros.StringMacros.describe"))
    assertEquals(macroDefinition.kind, DefKind.Def)
    assert(macroDefinition.expandsAtCallSite, macroDefinition.toString)
  }

  test("a Scala 2 macro definition records the implementation it expands into") {
    val implementations = TestProject213.index.macroImplementations.map(_.toDottedName)
    assert(implementations.contains("spec.macros.MacroImplementations.describeImpl"), implementations.toString)
  }

  test("every name a Scala 2 definition binds is indexed, whatever the pattern binding it") {
    Vector(
      "spec.bindings.BoundNames.firstOfTwo",
      "spec.bindings.BoundNames.secondOfTwo",
      "spec.bindings.BoundNames.leftOfPair",
      "spec.bindings.BoundNames.unwrapped"
    ).foreach(query => assertEquals(TestProject213.index.defs(TestProject213.symbolOf(query)).kind, DefKind.Binding))
  }

  test("a Scala 2 declared var and secondary constructor are indexed") {
    val declared = TestProject213.index.defs(TestProject213.symbolOf("spec.bindings.DeclaresMutableMember.declaredVar"))
    assertEquals(declared.kind, DefKind.Var)
    assert(declared.isAbstract, declared.toString)
    val constructors = TestProject213.index.defs.values.filter(node =>
      node.dottedName.startsWith("spec.bindings.TakesTwoParameters") && node.symbol.isConstructorSymbol
    )
    assertEquals(constructors.size, 1)
  }

}
