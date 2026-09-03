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

class ScalacOptionsSuite extends munit.FunSuite {

  test("options the sliced project compiled with reach the slice") {
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-Xkind-projector", "-source:future", "-release", "17")),
      Vector("-Xkind-projector", "-source:future", "-release", "17")
    )
  }

  test("warnings are not errors in a slice") {
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-Xfatal-warnings", "-Werror", "-Wconf:cat=deprecation:e", "-deprecation")),
      Vector("-deprecation")
    )
  }

  test("the semanticdb the slicer read from is not asked for again") {
    assertEquals(
      ScalacOptions.filterForSlice(
        Vector("-Xsemanticdb", "-sourceroot", "/home/dev/project", "-P:semanticdb:synthetics:on", "-deprecation")
      ),
      Vector("-deprecation")
    )
  }

  test("a compiler plugin loaded by path is dropped, since the slice declares it as a dependency") {
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-Xplugin:/home/dev/.cache/kind-projector.jar", "-Xkind-projector")),
      Vector("-Xkind-projector")
    )
  }

  test("every module's semanticdb target goes, values and all") {
    assertEquals(
      ScalacOptions.filterForSlice(
        Vector(
          "-deprecation",
          "-semanticdb-target",
          "/home/dev/project/core/meta",
          "-deprecation",
          "-semanticdb-target",
          "/home/dev/project/util/meta"
        )
      ),
      Vector("-deprecation")
    )
  }

  test("an option every module passed is written once") {
    assertEquals(ScalacOptions.filterForSlice(Vector("-deprecation", "-deprecation")), Vector("-deprecation"))
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-release", "17", "-deprecation", "-release", "17")),
      Vector("-release", "17", "-deprecation")
    )
  }

  test("an option keeps its value when another option was given the same one") {
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-release", "8", "-Xmax-inlines", "8")),
      Vector("-release", "8", "-Xmax-inlines", "8")
    )
  }

  test("modules disagreeing on a value both reach the slice, in the order they gave it") {
    assertEquals(
      ScalacOptions.filterForSlice(Vector("-release", "17", "-release", "8")),
      Vector("-release", "17", "-release", "8")
    )
  }
}
