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

import slicer.harness.{Slice, TestProject213}

class Scala213ReachabilitySuite extends munit.FunSuite {

  private def keptNames(slice: Slice): Set[String] =
    slice.result.kept.map(_.toDottedName)

  test("the Scala 2 corpus parses with a Scala 2 dialect, early initializers included") {
    val kept = keptNames(TestProject213.slice("spec.earlyinit.CallsEarlyInitializer.call"))
    assert(kept.contains("spec.earlyinit.InitializesBeforeMixin"), kept.toString)
    assert(kept.contains("spec.earlyinit.ReadsInitializedField.derivedFromField"), kept.toString)
  }

  test("a type alias nothing in the slice names goes with the rest of the object's unused members") {
    val used = keptNames(TestProject213.slice("spec.external.CallsLibrary.checksName"))
    assert(used.contains("spec.external.CallsLibrary.LibraryAlias"), used.toString)
    val unused = keptNames(TestProject213.slice("spec.external.CallsLibrary.reducesWithLibraryImplicit"))
    assert(!unused.contains("spec.external.CallsLibrary.LibraryAlias"), unused.toString)
  }

  test("an implicit argument reaches its instance through the synthetics scalac records") {
    val kept = keptNames(TestProject213.slice("spec.implicits.CallsTypeClass.rendersList"))
    assert(kept.contains("spec.implicits.TypeClass.typeClassForList"), kept.toString)
    assert(kept.contains("spec.implicits.TypeClass.typeClassForInt"), kept.toString)
  }

  test("a lower-priority instance in a parent trait is kept when it is the one selected") {
    val kept = keptNames(TestProject213.slice("spec.implicits.CallsPriorityTypeClass.picksFallbackInstance"))
    assert(kept.contains("spec.implicits.LowPriorityInstances.fallbackInstance"), kept.toString)
  }

  test("an implicit def conversion in a touched package is kept even though nothing names it") {
    val kept = keptNames(TestProject213.slice("spec.conversions.CallsConversions.convertsImplicitly"))
    assert(kept.contains("spec.conversions.ConversionsInScope.convertsStringToLength"), kept.toString)
  }

  test("an implicit class is kept for the extension method its user calls") {
    val slice = TestProject213.slice("spec.implicitclasses.CallsImplicitClasses.callsValueClassSyntax")
    val kept = keptNames(slice)
    assert(kept.contains("spec.implicitclasses.StringSyntax.ImplicitClassOnString"), kept.toString)
    assert(kept.contains("spec.implicitclasses.StringSyntax.ImplicitClassOnString.shouted"), kept.toString)
  }

  test("writing a var keeps the var, not just its setter") {
    val slice = TestProject213.slice("spec.values.HoldsValues.writesMutableValue")
    assert(keptNames(slice).contains("spec.values.HoldsValues.mutableValue"), keptNames(slice).toString)
    assert(slice.file("values/Values.scala").contains("var mutableValue"), slice.file("values/Values.scala"))
  }

  test("a member of a package object is reachable like any other member") {
    val kept = keptNames(TestProject213.slice("spec.legacy.package.definitionInPackageObject"))
    assert(kept.contains("spec.legacy.package.valueInPackageObject"), kept.toString)
  }

  test("a slice of code that uses a library keeps the library's implicit instance") {
    val kept = keptNames(TestProject213.slice("spec.external.CallsLibrary.reducesWithLibraryImplicit"))
    assert(kept.contains("spec.external.HasLibraryImplicit.semigroupForLibraryValue"), kept.toString)
  }

  test("a macro call keeps the implementation it expands into and leaves the others behind") {
    val kept = keptNames(TestProject213.slice("spec.macros.CallsMacros.callsDescribe"))
    assert(kept.contains("spec.macros.MacroImplementations.describeImpl"), kept.toString)
    assert(!kept.contains("spec.macros.MacroImplementations.sizeImpl"), kept.toString)
  }

  test("a class a macro looks up by name is kept with the members the expansion reads") {
    val kept = keptNames(TestProject213.slice("spec.macros.CallsMacros.callsReflectedLabel"))
    assert(kept.contains("spec.macros.ReflectedTarget"), kept.toString)
    assert(kept.contains("spec.macros.ReflectedTarget.label"), kept.toString)
  }

  test("reading one of two names bound together keeps the Scala 2 definition that binds both") {
    val body = TestProject213.slice("spec.bindings.ReadsBoundNames.readsFirstOfTwo").file("bindings/Bindings.scala")
    assert(body.contains("val firstOfTwo, secondOfTwo = 1"), body)
    assert(!body.contains("leftOfPair"), body)
  }

  test("instantiating a Scala 2 class keeps the secondary constructors it could have been built with") {
    val body =
      TestProject213.slice("spec.bindings.ReadsBoundNames.readsSecondaryConstructor").file("bindings/Bindings.scala")
    assert(body.contains("def this(label: String) = this(label, 1)"), body)
  }

}
