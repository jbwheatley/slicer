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

import java.nio.file.Paths

class EmitRelativeSuite extends munit.FunSuite {

  private val sourceRoot = Paths.get("/work/project")

  test("a file under the project root keeps the path it was written at") {
    assertEquals(
      Emit.toRelativeSlicePath(sourceRoot, Paths.get("/work/project/base/src/main/scala/spec/Types.scala")),
      Paths.get("base/src/main/scala/spec/Types.scala")
    )
  }

  test("a cross-built file keeps the version suffix of the root it was written in") {
    assertEquals(
      Emit.toRelativeSlicePath(sourceRoot, Paths.get("/work/project/base/src/main/scala-3/spec/Types.scala")),
      Paths.get("base/src/main/scala-3/spec/Types.scala")
    )
  }

  test("files sharing a name in modules outside the project root are placed apart") {
    val first = Emit.toRelativeSlicePath(sourceRoot, Paths.get("/work/shared/src/main/scala/spec/base/Ops.scala"))
    val second = Emit.toRelativeSlicePath(sourceRoot, Paths.get("/work/util/src/main/scala/spec/util/Ops.scala"))

    assertEquals(first, Paths.get("shared/src/main/scala/spec/base/Ops.scala"))
    assertEquals(second, Paths.get("util/src/main/scala/spec/util/Ops.scala"))
  }

  test("a file under no source root at all is placed among the generated sources") {
    assertEquals(
      Emit.toRelativeSlicePath(sourceRoot, Paths.get("/work/loose/Stray.scala")),
      Paths.get("generated/src/main/scala/Stray.scala")
    )
  }
}
