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

import slicer.harness.{Slice, TestProject213}

class Scala213GoldenSuite extends munit.FunSuite {

  private val directory: Path =
    TestProject213.repoRoot.resolve("slicer-core/src/test/resources/golden-213")

  private val roots = Vector(
    "spec.entry.Handler.handlesWithOneParameter",
    "spec.entry.Handler.handlesWithImplicitClass",
    "spec.implicits.CallsTypeClass.rendersPair",
    "spec.implicits.CallsPriorityTypeClass.picksFallbackInstance",
    "spec.implicits.CallsImplicitsInCompanionScope.rendersFromCompanion",
    "spec.implicitclasses.CallsImplicitClasses.callsValueClassSyntax",
    "spec.implicitclasses.CallsValueClass.call",
    "spec.conversions.CallsConversions.convertsThroughCompanion",
    "spec.earlyinit.CallsEarlyInitializer.call",
    "spec.sam.CallsSam.passesMethodReference",
    "spec.legacy.package.readsAlias",
    "spec.external.CallsLibrary.reducesWithLibraryImplicit",
    "spec.macros.CallsMacros.callsDescribe",
    "spec.macros.CallsMacros.callsReflectedLabel",
    "spec.bindings.ReadsBoundNames.readsFirstOfTwo",
    "spec.bindings.ReadsBoundNames.readsLeftOfPair",
    "spec.bindings.ReadsBoundNames.readsUnwrapped",
    "spec.bindings.ReadsBoundNames.readsSecondaryConstructor",
    "spec.bindings.DeclaresMutableMember.readsDeclaredVar"
  )

  roots.foreach { query =>
    test(s"slice of $query is unchanged") {
      val slice = TestProject213.slice(query)
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
