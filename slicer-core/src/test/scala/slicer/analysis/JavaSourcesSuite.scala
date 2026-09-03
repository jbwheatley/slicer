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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slicer.harness.Workspace
import slicer.model.DefKind

import cats.syntax.eq.*

class JavaSourcesSuite extends munit.FunSuite {

  private val workspace: FunFixture[Path] =
    FunFixture[Path](setup = _ => Workspace.create("slice-java-"), teardown = Workspace.delete)

  private def factsFor(out: Path, name: String, text: String): JavaFileData = {
    val file = out.resolve(name)
    Files.createDirectories(file.getParent)
    Files.write(file, text.getBytes(StandardCharsets.UTF_8))
    JavaSources.readJavaFile(file) match {
      case Left(error)  => fail(error.getMessage)
      case Right(facts) => facts
    }
  }

  workspace.test("a type takes the symbol its package and name give it") { out =>
    val facts = factsFor(
      out = out,
      name = "a/b/Widget.java",
      text = """package a.b;
        |
        |public final class Widget {
        |}
        |""".stripMargin
    )

    assertEquals(facts.nodes.map(node => (node.symbol.value, node.kind)), Vector(("a/b/Widget#", DefKind.JavaType)))
  }

  workspace.test("a nested type hangs off the type that declares it") { out =>
    val facts = factsFor(
      out = out,
      name = "a/b/Outer.java",
      text = """package a.b;
        |
        |public class Outer {
        |  public static class Inner {
        |  }
        |}
        |""".stripMargin
    )

    assertEquals(
      facts.nodes.filter(_.kind === DefKind.JavaType).map(node => (node.symbol.value, node.owner.map(_.value))),
      Vector(("a/b/Outer#", None), ("a/b/Outer#Inner#", Some("a/b/Outer#")))
    )
  }

  workspace.test("methods are not nodes, so they can never be offered as a slice root") { out =>
    val facts = factsFor(
      out = out,
      name = "a/b/Widget.java",
      text = """package a.b;
        |
        |public final class Widget {
        |  public String describe() {
        |    return "x";
        |  }
        |}
        |""".stripMargin
    )

    assertEquals(facts.nodes.map(_.kind).distinct, Vector(DefKind.JavaType))
  }

  workspace.test("a declaration written inside a comment or a string is not a declaration") { out =>
    val facts = factsFor(
      out = out,
      name = "a/b/Widget.java",
      text = """package a.b;
        |
        |public final class Widget {
        |  private static final String SOURCE = "class Ghost {}";
        |}
        |""".stripMargin
    )

    assertEquals(facts.nodes.map(_.displayName), Vector("Widget"))
  }

  workspace.test("an imported name, a package mate and a star import all become references") { out =>
    val facts = factsFor(
      out = out,
      name = "a/b/Widget.java",
      text = """package a.b;
        |
        |import a.b.tools.Formatter;
        |import a.c.*;
        |
        |public final class Widget {
        |  public String describe() {
        |    return Formatter.format(Marker.SUFFIX) + Helper.NAME;
        |  }
        |}
        |""".stripMargin
    )

    val references = facts.references.map(_._2).distinct
    assert(references.contains("a.b.tools.Formatter"), references.toString)
    assert(references.contains("a.b.Marker"), references.toString)
    assert(references.contains("a.c.Helper"), references.toString)
  }

  workspace.test("only Java files are picked up from the source directories") { out =>
    Files.createDirectories(out.resolve("src"))
    Files.write(out.resolve("src/Widget.java"), "package a;".getBytes(StandardCharsets.UTF_8))
    Files.write(out.resolve("src/Widget.scala"), "package a".getBytes(StandardCharsets.UTF_8))

    assertEquals(JavaSources.listJavaFilesUnder(Vector(out)).map(_.getFileName.toString), Vector("Widget.java"))
  }
}
