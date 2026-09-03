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

class GeneratedMemberSuite extends munit.FunSuite {

  test("the members a case class construction goes through are the factory ones plus copy") {
    assertEquals(
      GeneratedMember.constructionEntries.map(_.memberName),
      Vector("apply", "unapply", "unapplyVector", "copy")
    )
    assertEquals(GeneratedMember.factoryEntries.map(_.memberName), Vector("apply", "unapply", "unapplyVector"))
  }

  test("a factory entry is recognised by name, and copy is not one") {
    assert(GeneratedMember.isFactoryEntry("apply"))
    assert(GeneratedMember.isFactoryEntry("unapply"))
    assert(GeneratedMember.isFactoryEntry("unapplyVector"))
    assert(!GeneratedMember.isFactoryEntry("copy"))
    assert(!GeneratedMember.isFactoryEntry("derived"))
  }

  test("only `derived` is a derivation") {
    assert(GeneratedMember.isDerivation("derived"))
    assert(!GeneratedMember.isDerivation("apply"))
    assert(!GeneratedMember.isDerivation("Derived"))
  }
}
