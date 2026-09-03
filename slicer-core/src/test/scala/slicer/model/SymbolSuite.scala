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

package slicer.model

class SymbolSuite extends munit.FunSuite {

  test("a symbol reads back as the name a person would type") {
    assertEquals(Symbol("spec/entry/Handler#handles().").toDottedName, "spec.entry.Handler.handles")
    assertEquals(Symbol("spec/entry/Handler#").toDottedName, "spec.entry.Handler")
    assertEquals(Symbol("spec/entry/Registry.").toDottedName, "spec.entry.Registry")
    assertEquals(Symbol("spec/entry/Registry.entry().value.").toDottedName, "spec.entry.Registry.entry.value")
  }

  test("only compiler-local symbols count as local") {
    assert(Symbol("local12").isLocalSymbol)
    assert(!Symbol("spec/entry/Handler#").isLocalSymbol)
  }

  test("a directory name keeps letters and digits and nothing else") {
    assertEquals(Symbol("spec/entry/Handler#handles().").toDirectoryName, "spec-entry-Handler-handles")
    assertEquals(Symbol("_root_/A#").toDirectoryName, "root-A")
  }

  test("the package of a symbol stops at the first type or parameter list") {
    assertEquals(Symbol("spec/entry/Handler#handles().").toPackagePrefix, "spec/entry/")
    assertEquals(Symbol("spec/entry/Registry.").toPackagePrefix, "spec/entry/")
    assertEquals(Symbol("Top#").toPackagePrefix, "")
  }

  test("a class and its companion object are asked for together") {
    assertEquals(Symbol("spec/A#").withCompanionSymbol, Set(Symbol("spec/A#"), Symbol("spec/A.")))
    assertEquals(Symbol("spec/A.").withCompanionSymbol, Set(Symbol("spec/A."), Symbol("spec/A#")))
    assertEquals(Symbol("spec/A.method().").withCompanionSymbol, Set(Symbol("spec/A.method().")))
  }

  test("only an object symbol names a companion class") {
    assertEquals(Symbol("spec/A.").findCompanionClass, Some(Symbol("spec/A#")))
    assertEquals(Symbol("spec/A#").findCompanionClass, None)
    assertEquals(Symbol("spec/A.method().").findCompanionClass, None)
  }

  test("a constructor and a companion factory both fix the parameters of their class") {
    assertEquals(Symbol("spec/A#`<init>`().").findParameterFixingOwner, Some(Symbol("spec/A#")))
    assertEquals(Symbol("spec/A.apply().").findParameterFixingOwner, Some(Symbol("spec/A#")))
    assertEquals(Symbol("spec/A.unapply().").findParameterFixingOwner, Some(Symbol("spec/A#")))
    assertEquals(Symbol("spec/A#handles().").findParameterFixingOwner, None)
  }

  test("a setter names the reader it was generated with") {
    assertEquals(Symbol("spec/A#count_=().").findGetterForSetter, Some(Symbol("spec/A#count().")))
    assertEquals(Symbol("spec/A#count().").findGetterForSetter, None)
  }

  test("the owner of a symbol drops its last descriptor") {
    assertEquals(Symbol("spec/A#handles().").findOwnerSymbol, Some(Symbol("spec/A#")))
    assertEquals(Symbol("spec/A#").findOwnerSymbol, Some(Symbol("spec/")))
    assertEquals(Symbol("spec/").findOwnerSymbol, None)
  }

  test("members of the universal supertypes are recognised") {
    assert(Symbol("java/lang/Object#toString().").isUniversalMember)
    assert(Symbol("scala/Any#equals().").isUniversalMember)
    assert(!Symbol("spec/A#toString().").isUniversalMember)
  }
}
