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

package slicer.emit

import slicer.harness.TestProject
import slicer.model.SliceOptions

import cats.syntax.eq.*

class EmitSuite extends munit.FunSuite {

  test("a comment above a dropped definition goes with it") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    val services = slice.file("services/Services.scala")
    assert(services.contains("// reached from Handler.handlesWithOneParameter"))
    assert(!services.contains("// reached from Handler.handlesWithBothParameters only"), services)
  }

  test("an importee naming a member of an object is dropped when that member died") {
    val body = TestProject
      .slice("spec.imports.ReadsImportedMembers.readsUsedConstant")
      .file("imports/ReadsImportedMembers.scala")
    assert(body.contains("import spec.imports.ImportedMembers.usedConstant"), body)
    assert(!body.contains("unusedConstant"), body)
  }

  test("a wildcard import of a package nothing survived in is dropped") {
    val body = TestProject
      .slice("spec.imports.ReadsImportedMembers.readsUsedConstant")
      .file("imports/ReadsImportedMembers.scala")
    assert(!body.contains("spec.imports.wholly"), body)
  }

  test("an emptied body keeps the self type the definition declares") {
    val body = TestProject.slice("spec.inheritance.NamesSelfTyped.names").file("inheritance/Inheritance.scala")
    assert(body.contains("trait RequiresSelfType { self: HasStoreName => }"), body)
  }

  test("an importee whose target died is dropped, and the import with it when all of them do") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    val handler = slice.file("entry/Handler.scala")
    assert(handler.contains("import spec.services.{FirstService, ServiceView}"), handler)
    assert(!handler.contains("SecondService"), handler)
    assert(handler.contains("import spec.givens.{CallsTypeClass, TypeClass}"), handler)
    assert(!handler.contains("import spec.opaques"), handler)
  }

  test("dropping the trailing parameters of a class takes their separators with them") {
    val slice = TestProject.slice("spec.services.ServiceView.key")
    val services = slice.file("services/Services.scala")
    assert(services.contains("case class ServiceView(key: Long)"), services)
  }

  test("an importer left with one importee loses its braces") {
    val slice = TestProject.slice("spec.entry.Handler.first")
    val handler = slice.file("entry/Handler.scala")
    assert(handler.contains("import spec.services.FirstService"), handler)
    assert(!handler.contains("{FirstService"), handler)
  }

  test("a body whose statements all died collapses instead of leaving empty braces") {
    val slice = TestProject.slice("spec.entry.Handler.first")
    val implementations = slice.file("implementations/Implementations.scala")
    assert(implementations.contains("trait AbstractWithTwoMembers {}"), implementations)
  }

  test("an indented body whose statements all died loses its colon rather than gaining braces") {
    val slice = TestProject.slice("spec.indentation.HoldsIndentedMembers.name")
    val indented = slice.file("indentation/Indented.scala")
    assert(indented.contains("trait IndentedMembers\n"), indented)
    assert(!indented.contains("trait IndentedMembers {}"), indented)
    assert(!indented.contains("firstMember"), indented)
  }

  test("the end marker of a dropped definition goes with it") {
    val slice = TestProject.slice("spec.indentation.IndentedEnum.reasonOrEmpty")
    val indented = slice.file("indentation/Indented.scala")
    assert(!indented.contains("end IndentedClass"), indented)
    assert(!indented.contains("end IndentedObject"), indented)
  }

  test("an indented given whose members all died keeps a body rather than a dangling colon") {
    val slice = TestProject.slice("spec.indentation.IndentedGivens.given_Renderer_Int")
    val indented = slice.file("indentation/Indented.scala")
    assert(indented.contains("given Renderer[Int] {}"), indented)
  }

  test("a given written with `with` whose members all died trades the keyword for a body") {
    val slice = TestProject.slice("spec.indentation.IndentedGivens.stringRenderer")
    val indented = slice.file("indentation/Indented.scala")
    assert(indented.contains("given stringRenderer: Renderer[String] {}"), indented)
    assert(!indented.contains("with"), indented)
  }

  test("a renamed importee is pruned on the original name, not the alias") {
    val exports =
      TestProject.allSymbols.filter(d =>
        d.dottedName.contains("NestedObject") || d.dottedName.contains("renamedExport")
      )
    assert(exports.nonEmpty, "no renaming corner in the corpus")
    exports.foreach { root =>
      val slice = TestProject.sliceOf(root, SliceOptions(followImplementations = true, keepFields = false))
      assert(!slice.text.contains("import {"), slice.text)
    }
  }

  test("an extension group whose methods all died is removed whole") {
    val slice = TestProject.slice("spec.opaques.CallsExtensionMethod.calls")
    val opaque = slice.file("opaques/Opaque.scala")
    assert(opaque.contains("def usedExtensionMethod"))
    assert(!opaque.contains("def unusedExtensionMethod"), opaque)
    assert(!opaque.contains("extension (value: OpaqueType)"), opaque)
  }

  test("a companion whose members all died is dropped") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithOneParameter")
    assert(slice.text.contains("class FirstService(source: AbstractWithTwoMembers)"), slice.text)
    assert(!slice.text.contains("object FirstService"), slice.text)
  }

  test("several surviving implementations of one member are labelled at both ends") {
    val slice = TestProject.slice("spec.implementations.AbstractWithTwoMembers.calledMember")
    val implementations = slice.file("implementations/Implementations.scala")
    assert(
      implementations.contains("// slice: 2 impls kept (DelegatingImplementation, DirectImplementation)"),
      implementations
    )
    assert(
      implementations.contains("// slice: one of 2 impls of AbstractWithTwoMembers.calledMember kept"),
      implementations
    )
  }

  test("emitted files keep their original formatting") {
    val slice = TestProject.slice("spec.entry.Handler.handlesWithBothParameters")
    val handler = slice.file("entry/Handler.scala")
    assert(handler.contains("  def handlesWithBothParameters(input: InputValue): ResultValue =\n    second."), handler)
    assert(handler.endsWith("\n"))
    assert(!handler.contains("\n\n\n"), "blank runs should be collapsed")
  }

  test("a block comment above a dropped definition goes with it, whole") {
    val slice = TestProject.slice("spec.comments.CallsBlockCommented.calls")
    val comments = slice.file("comments/Comments.scala")
    assert(comments.contains("Kept definition documented across lines"), comments)
    assert(comments.contains("with a continuation that has no star"), comments)
    assert(!comments.contains("Dropped definition documented across lines"), comments)
    assert(!comments.contains("Dropped definition with an aligned block comment"), comments)
    assertEquals(countOf("/*", comments), countOf("*/", comments), comments)
  }

  test("a file with nothing kept is not emitted at all") {
    val slice = TestProject.slice("spec.opaques.CallsExtensionMethod.calls")
    assert(!slice.files.keys.exists(_.endsWith("entry/Handler.scala")), slice.files.keys.mkString(", "))
  }

  private def countOf(token: String, text: String): Int = text.sliding(token.length).count(_ === token)
}
