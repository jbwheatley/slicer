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

import slicer.harness.{Slice, TestProject}
import slicer.model.{DefKind, SliceOptions}

import cats.syntax.eq.*

class ReachabilitySuite extends munit.FunSuite {

  private def keptNames(slice: Slice): Set[String] =
    slice.result.kept.map(_.toDottedName)

  test("unused constructor parameters are pruned") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    assert(slice.file("entry/Handler.scala").contains("class Handler(first: FirstService)"))
    assert(!keptNames(slice).exists(_.endsWith("Handler.second")))
  }

  test("a bare companion reference pins the case class's parameters") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    assert(slice.file("entry/Handler.scala").contains("case class ResultValue(code: Int, body: String)"))
  }

  test("reaching an abstract member reaches its implementations, and their constructor arguments") {
    val slice = TestProject.slice("spec.implementations.AbstractWithTwoMembers.calledMember")
    val kept = keptNames(slice)
    assert(
      kept.contains("spec.implementations.DirectImplementation.calledMember"),
      kept.filter(_.contains("DirectImplementation")).toString
    )
    assert(kept.contains("spec.implementations.DelegatingImplementation.calledMember"))
    assert(kept.contains("spec.implementations.SourceImplementation"))
  }

  test("a class root keeps every member, and what those members reach") {
    val slice = TestProject.slice("spec.services.SecondService")
    val kept = keptNames(slice)
    assert(kept.contains("spec.services.SecondService.readsBothSources"))
    assert(kept.contains("spec.services.SecondService.joins"))
    assert(kept.contains("spec.implementations.AbstractWithTwoMembers.calledMember"), kept.toString)
  }

  test("a member named on one implementation leaves the sibling implementations out") {
    val kept = keptNames(TestProject.slice("spec.implementations.DirectImplementation.calledMember"))
    assert(kept.contains("spec.implementations.AbstractWithTwoMembers.calledMember"), kept.toString)
    assert(!kept.exists(_.contains("DelegatingImplementation")), kept.toString)
  }

  test("a kept type keeps the members its supertypes define, bodies and all") {
    val kept = keptNames(TestProject.slice("spec.inheritance.MixesInSelfType.storeName"))
    assert(kept.contains("spec.inheritance.HasStoreName.lazyBanner"), kept.toString)
    assert(kept.contains("spec.inheritance.RequiresSelfType.usesSelfType"), kept.toString)
  }

  test("a call through a wildcard export reaches the definition behind the forwarder") {
    val kept = keptNames(TestProject.slice("spec.nested.CallsWildcardExport.callsForwardedMember"))
    assert(kept.contains("spec.nested.HasNestedObject.NestedObject.readsNestedValue"), kept.toString)
  }

  test("--no-impls stops at the abstract member") {
    val slice = TestProject.slice(
      "spec.implementations.AbstractWithTwoMembers.calledMember",
      SliceOptions(followImplementations = false, keepFields = false)
    )
    assert(!keptNames(slice).exists(_.contains("DirectImplementation")))
  }

  test("a named implementation does not keep the abstract members nobody called") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    val implementations = slice.file("implementations/Implementations.scala")
    assert(implementations.contains("def calledMember(key: Long)"))
    assert(!implementations.contains("def uncalledMember()"), implementations)
  }

  test("an inline implementation keeps every abstract member of the type it implements") {
    val slice = TestProject.slice("spec.givens.CallsContextFunction.callsWithAnonymousImplementation")
    assert(keptNames(slice).contains("spec.givens.ContextParameter.value"))
  }

  test("a kept trait member keeps the overrides of every kept implementation, to fixpoint") {
    val slice = TestProject.slice("spec.members.CallsConcreteMember.viaTrait")
    val kept = keptNames(slice)
    assert(kept.contains("spec.members.AbstractAndConcrete.concreteMember"))
    assert(kept.contains("spec.members.OverridesConcreteMember.concreteMember"))
  }

  test("an override keeps the member it overrides") {
    val slice = TestProject.slice("spec.members.OverridesConcreteMember.concreteMember")
    assert(keptNames(slice).contains("spec.members.AbstractAndConcrete.concreteMember"))
  }

  test("a kept enum keeps its cases") {
    val slice = TestProject.slice("spec.enums.MatchesEnums.matchesSimpleEnum")
    val enums = slice.file("enums/Enums.scala")
    assert(enums.contains("case WithoutParameters"))
    assert(enums.contains("case WithOneParameter(reason: String)"))
  }

  test("both halves of a class/companion pair resolve from a pattern") {
    val slice = TestProject.slice("spec.enums.MatchesEnums.matchesSealedTrait")
    val enums = slice.file("enums/Enums.scala")
    assert(enums.contains("final case class SealedCaseClass(id: Long)"))
    assert(enums.contains("case object SealedCaseObject"))
  }

  test("`derives` pulls the type class and its `derived` member") {
    val slice = TestProject.slice("spec.derivation.CallsDerivedInstance.rendersDerived")
    val kept = keptNames(slice)
    assert(kept.contains("spec.derivation.DerivableTypeClass.derived"))
    assert(kept.contains("spec.derivation.DerivableTypeClass.label"))
    assert(slice.file("derivation/Derivation.scala").contains("derives DerivableTypeClass"))
  }

  test("a class whose shape is fixed by `derives` keeps its parameters") {
    val slice = TestProject.slice("spec.derivation.CallsDerivedInstance.rendersDerived")
    assert(slice.file("derivation/Derivation.scala").contains("case class DerivesTypeClass(name: String)"))
  }

  test("an object reference with an argument list keeps that object's apply/unapply") {
    val slice = TestProject.slice("spec.toplevel.CallsUnapply.matchesWithUnapply")
    assert(keptNames(slice).contains("spec.toplevel.HasUnapplyInCompanion.unapply"))
  }

  test("an auxiliary constructor pins the primary parameter list") {
    val slice = TestProject.slice("spec.members.CallsConcreteMember.viaAuxiliaryConstructor")
    val members = slice.file("members/Members.scala")
    assert(members.contains("def this(abstractMember: String)"))
    assert(members.contains("class OverridesConcreteMember(val abstractMember: String, val page: Int)"))
  }

  test("a kept class keeps its type members") {
    val slice = TestProject.slice("spec.types.CallsRefinedType.readsRefined")
    assert(keptNames(slice).contains("spec.types.BindsAbstractTypeMember.Key"))
    val types = slice.file("types/Types.scala")
    assert(types.contains("trait HasAbstractTypeMember {\n  type Key"), types)
    assert(types.contains("object BindsAbstractTypeMember extends HasAbstractTypeMember {\n  type Key = String"), types)
  }

  test("an inline method keeps the givens in scope at its definition") {
    val inlines = TestProject.allSymbols.filter(_.expandsAtCallSite)
    assert(inlines.nonEmpty, "the test corpus has no inline definitions")
    inlines.foreach { root =>
      val slice = TestProject.sliceOf(root, SliceOptions(followImplementations = true, keepFields = false))
      assert(slice.files.nonEmpty, s"inline root ${root.dottedName} sliced to nothing")
    }
  }

  test("slicing an extension group keeps the methods in it") {
    val groups = TestProject.allSymbols.filter(_.kind === DefKind.Extension)
    assert(groups.nonEmpty, "the corpus has no extension groups")
    groups.foreach { group =>
      val slice = TestProject.sliceOf(group, SliceOptions(followImplementations = true, keepFields = false))
      assert(!slice.text.contains("{}"), s"${group.symbol} sliced to an empty group:\n${slice.text}")
    }
  }

  test("a member implementing an abstract member of a library type stays with the type that carries it") {
    val slice = TestProject.slice("spec.external.CallsLibrary.reducesWithLibraryGiven")
    val kept = keptNames(slice)
    assert(
      kept.contains("spec.external.HasLibraryGiven.given_Semigroup_HasLibraryGiven.combine"),
      kept.toString
    )
    assert(
      slice.file("external/External.scala").contains("def combine(left: HasLibraryGiven, right: HasLibraryGiven)")
    )
  }

  test("a given nobody reached is dropped whole, body and all") {
    val slice = TestProject.slice("spec.external.CallsLibrary.combinesChecks")
    val external = slice.file("external/External.scala")
    assert(!external.contains("given Semigroup"), external)
    assert(external.contains("case class HasLibraryGiven"), external)
  }

  test("a kept field's initializer is dropped unless --keep-fields") {
    val lean = TestProject.slice("spec.implementations.DelegatingImplementation.uncalledMember")
    val fat = TestProject.slice(
      "spec.implementations.DelegatingImplementation.uncalledMember",
      SliceOptions(followImplementations = true, keepFields = true)
    )
    assert(fat.result.kept.size > lean.result.kept.size, s"lean=${lean.result.kept.size} fat=${fat.result.kept.size}")
  }

  test("an object a macro looks up by name is kept with the members the expansion reads") {
    val kept = TestProject.slice("spec.macros.CallsMacros.callsReflectedLabel").result.kept.map(_.toDottedName)
    assert(kept.contains("spec.macros.MacroImplementations.reflectedLabelImpl"), kept.toString)
    assert(kept.contains("spec.macros.ReflectedTarget"), kept.toString)
    assert(kept.contains("spec.macros.ReflectedTarget.label"), kept.toString)
  }

  test("reading one of two names bound together keeps the definition that binds both") {
    val slice = TestProject.slice("spec.bindings.ReadsBoundNames.readsFirstOfTwo")
    val body = slice.file("bindings/Bindings.scala")
    assert(body.contains("val firstOfTwo, secondOfTwo = 1"), body)
    assert(!body.contains("leftOfPair"), body)
  }

  test("a name bound by a pattern reaches the definition binding it") {
    val body = TestProject.slice("spec.bindings.ReadsBoundNames.readsUnwrapped").file("bindings/Bindings.scala")
    assert(body.contains("val Some(unwrapped) = Option(\"bound\")"), body)
    assert(!body.contains("firstOfTwo"), body)
  }

  test("instantiating a class keeps the secondary constructors it could have been built with") {
    val body =
      TestProject.slice("spec.bindings.ReadsBoundNames.readsSecondaryConstructor").file("bindings/Bindings.scala")
    assert(body.contains("def this(label: String) = this(label, 1)"), body)
  }

  test("a declared var is kept with the implementation that fixes it") {
    val kept =
      TestProject.slice("spec.bindings.DeclaresMutableMember.readsDeclaredVar").result.kept.map(_.toDottedName)
    assert(kept.contains("spec.bindings.DeclaresMutableMember.declaredVar"), kept.toString)
    assert(kept.contains("spec.bindings.FixesMutableMember.declaredVar"), kept.toString)
  }

  test("a summon through an exported given reaches the instance behind the forwarder") {
    val kept = keptNames(TestProject.slice("spec.exports.CallsExportedGiven.encodesInt"))
    assert(kept.contains("spec.exports.ExportedInstances.exportedIntInstance"), kept.toString)
    assert(kept.contains("spec.exports.ExportedTypeClass.encode"), kept.toString)
  }

  test("a conversion imported from another package is kept") {
    val kept = keptNames(TestProject.slice("spec.conversionusers.CallsImportedConversion.convertsAcrossPackages"))
    assert(kept.contains("spec.conversions.LengthConversions.lengthOfString"), kept.toString)
  }

  test("a conversion given written with a body is kept with the apply that converts") {
    val kept = keptNames(TestProject.slice("spec.conversionusers.CallsImportedConversion.wrapsAcrossPackages"))
    assert(kept.contains("spec.conversions.LengthConversions.wrapsLength"), kept.toString)
    assert(kept.contains("spec.conversions.LengthConversions.wrapsLength.apply"), kept.toString)
  }

  test("a lambda passed as an argument keeps the abstract member it implements") {
    val kept = keptNames(TestProject.slice("spec.sam.PassesLambdaAsArgument.passesLambda"))
    assert(kept.contains("spec.sam.Transformer.transform"), kept.toString)
    val slice = TestProject.slice("spec.sam.PassesLambdaAsArgument.passesLambdaWithDefault")
    assert(keptNames(slice).contains("spec.sam.TransformerWithDefault.transform"), keptNames(slice).toString)
    assert(slice.file("sam/Sam.scala").contains("def transform(value: String): String"), slice.file("sam/Sam.scala"))
  }

  test("a given keeps the concrete members the type it implements defines") {
    val kept = keptNames(TestProject.slice("spec.givenparents.DescriberInstances.intDescriber"))
    assert(kept.contains("spec.givenparents.Describer.describeAll"), kept.toString)
    val fromClass = keptNames(TestProject.slice("spec.givenparents.DescribesLongs"))
    assert(fromClass.contains("spec.givenparents.Describer.describeAll"), fromClass.toString)
  }

  test("a top-level inline definition keeps the givens in scope at its definition") {
    val kept = keptNames(TestProject.slice("spec.toplevelinline.TopLevelInline$package.encodesAtTopLevel"))
    assert(kept.contains("spec.toplevelinline.TopLevelInline$package.encoderForInt"), kept.toString)
    assert(kept.contains("spec.toplevelinline.Encoder.encode"), kept.toString)
  }

  test("an infix extractor pattern keeps the unapply behind it") {
    val kept = keptNames(TestProject.slice("spec.patterns.MatchesInfixExtractor.matchesInfix"))
    assert(kept.contains("spec.patterns.Joined.unapply"), kept.toString)
  }

  test("a call into a nested Java type keeps the file that declares it") {
    val slice = TestProject.slice("spec.javacalls.CallsNestedJava.describesInner")
    assert(slice.file("javadefs/Outer.java").contains("public static final class Inner"))
    assert(slice.file("javadefs/tools/Formatter.java").contains("public static String format"))
  }

  test("a call on a Java enum keeps the enum") {
    val slice = TestProject.slice("spec.javacalls.CallsNestedJava.labelOfStatus")
    assert(slice.file("javadefs/Status.java").contains("public String label()"))
  }

  test("a refinement member is kept with the type that carries it") {
    val slice = TestProject.slice("spec.refinements.ReadsRefinedMember.readsRefined")
    val refinements = slice.file("refinements/RefinedMembers.scala")
    assert(refinements.contains("HasStore { def extra: String }"), refinements)
    assert(keptNames(slice).contains("spec.refinements.HasStore.stored"), keptNames(slice).toString)
  }

  test("a fully qualified parent is kept with the members it defines") {
    val kept = keptNames(TestProject.slice("spec.qualified.ExtendsQualifiedParent.storeName"))
    assert(kept.contains("spec.inheritance.HasStoreName.lazyBanner"), kept.toString)
  }

  test("an intersection self-type keeps the members of both types it names") {
    val kept = keptNames(TestProject.slice("spec.qualified.MixesIntersectionSelfType.mixed"))
    assert(kept.contains("spec.qualified.FirstMixin.first"), kept.toString)
    assert(kept.contains("spec.qualified.SecondMixin.second"), kept.toString)
  }

  test("an interpolated pattern keeps the extractor behind it") {
    val kept = keptNames(TestProject.slice("spec.interpolators.MatchesInterpolatedPattern.matchesSegments"))
    assert(kept.contains("spec.interpolators.PathInterpolator.SegmentContext.segment.unapplySeq"), kept.toString)
  }

  test("an abstract type member nothing names is pruned with the members that named it") {
    val slice = TestProject.slice(
      "spec.abstracttypes.HasTwoTypeMembers.reads",
      SliceOptions(followImplementations = false, keepFields = false)
    )
    val types = slice.file("abstracttypes/AbstractTypeMembers.scala")
    assert(types.contains("type UsedType"), types)
    assert(!types.contains("type UnusedType"), types)
    assert(!types.contains("def writes"), types)
  }

  test("an abstract type member is kept with the implementation that binds it") {
    val types = TestProject
      .slice("spec.abstracttypes.CallsUsedTypeMember.calls")
      .file("abstracttypes/AbstractTypeMembers.scala")
    assert(types.contains("type UsedType = String"), types)
    assert(!types.contains("def writes"), types)
  }

  test("reading one of two names bound in a class body keeps the definition that binds both") {
    val body = TestProject.slice("spec.bindings.CallsBindsInClassBody.calls").file("bindings/BoundInClass.scala")
    assert(body.contains("val (firstField, secondField) = (seed, seed + 1)"), body)
    assert(!body.contains("thirdField"), body)
  }

}
