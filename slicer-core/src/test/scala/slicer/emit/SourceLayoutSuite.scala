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

class SourceLayoutSuite extends munit.FunSuite {

  test("a file under a source root keeps the root it was written in") {
    assertEquals(
      SourceLayout.findSourceRoot(Paths.get("base/src/main/scala/spec/Types.scala")),
      Some("base/src/main/scala")
    )
    assertEquals(
      SourceLayout.findSourceRoot(Paths.get("base/src/main/java/spec/Registry.java")),
      Some("base/src/main/java")
    )
  }

  test("a file under a cross-built source root keeps that root, version suffix and all") {
    assertEquals(
      SourceLayout.findSourceRoot(Paths.get("base/src/main/scala-3/spec/Types.scala")),
      Some("base/src/main/scala-3")
    )
    assertEquals(
      SourceLayout.findSourceRoot(Paths.get("base/src/main/scala-2.13/spec/Types.scala")),
      Some("base/src/main/scala-2.13")
    )
  }

  test("a file outside the sliced project's root is placed under the module it was written in") {
    assertEquals(
      SourceLayout.findModuleTail(Paths.get("/work/shared/src/main/scala/spec/Types.scala")),
      Some(Paths.get("shared/src/main/scala/spec/Types.scala"))
    )
    assertEquals(SourceLayout.findModuleTail(Paths.get("/work/loose/Stray.scala")), None)
  }

  test("only the newest of a module's cross-built roots is compiled") {
    assertEquals(
      SourceLayout.chooseCompiledRoots(
        Vector("base/src/main/scala", "base/src/main/scala-2.13", "base/src/main/scala-3")
      ),
      Vector("base/src/main/scala", "base/src/main/scala-3")
    )
  }

  test("cross-built roots are chosen per module, and Java is never one of them") {
    assertEquals(
      SourceLayout.chooseCompiledRoots(
        Vector(
          "base/src/main/scala-2.12",
          "base/src/main/scala-2.13",
          "entry/src/main/scala-2.13",
          "entry/src/main/java"
        )
      ),
      Vector("base/src/main/scala-2.13", "entry/src/main/java", "entry/src/main/scala-2.13")
    )
  }

  test("a directory a version suffix does not belong to is no source root") {
    assertEquals(SourceLayout.findSourceRoot(Paths.get("base/src/main/java-11/spec/Registry.java")), None)
  }

  test("a file under no source root has none to name") {
    assertEquals(SourceLayout.findSourceRoot(Paths.get("base/target/src_managed/main/spec/Version.scala")), None)
  }

  test("a source sbt generated keeps the package it was generated into") {
    assertEquals(
      SourceLayout.toGeneratedSourcePath(Paths.get("base/target/scala-3.8.4/src_managed/main/spec/gen/Version.scala")),
      Paths.get("generated/src/main/scala/spec/gen/Version.scala")
    )
  }

  test("a source mill generated keeps the package it was generated into") {
    assertEquals(
      SourceLayout.toGeneratedSourcePath(Paths.get("out/base/generatedSources.dest/spec/gen/Version.scala")),
      Paths.get("generated/src/main/scala/spec/gen/Version.scala")
    )
  }

  test("generated Java is placed beside the Scala rather than among it") {
    assertEquals(
      SourceLayout.toGeneratedSourcePath(Paths.get("out/base/generatedSources.dest/spec/gen/Version.java")),
      Paths.get("generated/src/main/java/spec/gen/Version.java")
    )
  }

  test("a source generated somewhere nothing recognises keeps its name and nothing else") {
    assertEquals(
      SourceLayout.toGeneratedSourcePath(Paths.get("somewhere/odd/Version.scala")),
      Paths.get("generated/src/main/scala/Version.scala")
    )
  }
}
