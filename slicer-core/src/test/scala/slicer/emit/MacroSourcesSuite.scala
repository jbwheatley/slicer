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

import slicer.harness.{TestProject, TestProject213}

class MacroSourcesSuite extends munit.FunSuite {

  test("a Scala 2 slice marks the source root holding its macro implementations as compiled first") {
    val slice = TestProject213.slice("spec.macros.CallsMacros.callsDescribe")
    assertEquals(slice.compiledFirst, Set("macros/src/main/scala/spec/macros/MacroImplementations.scala"))
    assert(
      slice.files.contains("base/src/main/scala/spec/macros/Macros.scala"),
      slice.files.keys.toVector.sorted.mkString(", ")
    )
  }

  test("a Scala 2 slice leaves the macro implementations at the paths they came from") {
    val slice = TestProject213.slice("spec.macros.CallsMacros.callsReflectedLabel")
    assert(
      slice.files.contains("macros/src/main/scala/spec/macros/MacroImplementations.scala"),
      slice.files.keys.toVector.sorted.mkString(", ")
    )
  }

  test("a Scala 3 slice compiles its macro implementations with everything else") {
    val slice = TestProject.slice("spec.macros.CallsMacros.callsDescribe")
    assertEquals(slice.compiledFirst, Set.empty[String])
  }

  test("a slice with no macro implementation in it needs nothing compiled first") {
    val slice = TestProject213.slice("spec.entry.Handler.handlesWithOneParameter")
    assertEquals(slice.compiledFirst, Set.empty[String])
  }
}
