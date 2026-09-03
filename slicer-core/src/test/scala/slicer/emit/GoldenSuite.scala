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

import java.nio.file.{Files, Path}

import slicer.harness.{Slice, TestProject}

class GoldenSuite extends munit.FunSuite {

  private val directory: Path =
    TestProject.repoRoot.resolve("slicer-core/src/test/resources/golden")

  private val roots = Vector(
    "spec.entry.Handler.handlesWithOneParameter",
    "spec.implementations.AbstractWithTwoMembers.calledMember",
    "spec.derivation.CallsDerivedInstance.rendersDerived",
    "spec.enums.MatchesEnums.matchesSimpleEnum",
    "spec.opaques.CallsExtensionMethod.calls",
    "spec.opaques.CallsOpaqueType.folds",
    "spec.members.CallsConcreteMember.viaAuxiliaryConstructor",
    "spec.types.CallsRefinedType.readsRefined",
    "spec.givens.CallsContextFunction.callsWithAnonymousImplementation",
    "spec.toplevel.CallsInterpolator.calls",
    "spec.toplevel.CallsUnapply.matchesWithUnapply",
    "spec.services.SecondService.readsBothSources",
    "spec.macros.CallsMacros.callsDescribe",
    "spec.macros.CallsMacros.callsReflectedLabel",
    "spec.bindings.ReadsBoundNames.readsFirstOfTwo",
    "spec.bindings.ReadsBoundNames.readsLeftOfPair",
    "spec.bindings.ReadsBoundNames.readsUnwrapped",
    "spec.bindings.ReadsBoundNames.readsRepeatedCase",
    "spec.bindings.ReadsBoundNames.readsSecondaryConstructor",
    "spec.bindings.DeclaresMutableMember.readsDeclaredVar"
  )

  roots.foreach { query =>
    test(s"slice of $query is unchanged") {
      val slice = TestProject.slice(query)
      val rendered = renderSliceForGolden(slice)
      val golden = directory.resolve(query.replace('.', '-') + ".slice")
      if (!Files.exists(golden)) {
        Files.createDirectories(directory)
        Files.writeString(golden, rendered)
        println(s"[golden] wrote ${directory.getParent.relativize(golden)} — review it before committing")
      } else assertNoDiff(rendered, Files.readString(golden))
    }
  }

  private def renderSliceForGolden(slice: Slice): String =
    slice.files.toVector
      .sortBy(_._1)
      .map { case (name, body) => s"--- $name\n$body" }
      .mkString("\n")
}
