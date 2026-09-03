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

import java.nio.file.{Files, Path}

import slicer.harness.Workspace

class SourceLocatorSuite extends munit.FunSuite {

  private val workspace = FunFixture[Path](
    setup = _ => Workspace.create("slice-source-locator-"),
    teardown = Workspace.delete
  )

  private def write(root: Path, relative: String): Path = {
    val file = root.resolve(relative)
    Files.createDirectories(file.getParent)
    Files.writeString(file, "object Top")
    file
  }

  workspace.test("a uri under the source root resolves against it") { root =>
    val file = write(root, "src/main/scala/spec/Top.scala")
    val locate = SourceLocator(root, Vector.empty)
    assertEquals(locate("src/main/scala/spec/Top.scala"), Some(file.normalize()))
  }

  workspace.test("a uri from another root is found in the directories it was given") { root =>
    val file = write(root, "modules/core/src/main/scala/spec/Top.scala")
    val locate = SourceLocator(root.resolve("elsewhere"), Vector(root.resolve("modules")))
    assertEquals(locate("src/main/scala/spec/Top.scala"), Some(file.normalize()))
  }

  workspace.test("the shortest path wins when several directories offer the same uri") { root =>
    val near = write(root, "a/spec/Top.scala")
    write(root, "a/nested/deeper/spec/Top.scala"): Unit
    val locate = SourceLocator(root.resolve("elsewhere"), Vector(root.resolve("a")))
    assertEquals(locate("spec/Top.scala"), Some(near.normalize()))
  }

  workspace.test("a uri nothing offers resolves to nothing") { root =>
    val locate = SourceLocator(root, Vector(root))
    assertEquals(locate("spec/Missing.scala"), None)
  }
}
