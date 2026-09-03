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

class PlatformSuite extends munit.FunSuite {

  test("a platform names itself the way both build tools suffix an artifact") {
    assertEquals(Platform.ScalaJs("1.19.0").artifactSuffix, "_sjs1")
    assertEquals(Platform.ScalaNative("0.5.8").artifactSuffix, "_native0.5")
    assertEquals(Platform.Jvm.artifactSuffix, "")
  }

  test("a platform prefixes the Scala binary version the way sbt cross-versions with it") {
    assertEquals(Platform.ScalaJs("1.19.0").crossPrefix, "sjs1_")
    assertEquals(Platform.ScalaNative("0.5.8").crossPrefix, "native0.5_")
    assertEquals(Platform.Jvm.crossPrefix, "")
  }

  test("a platform version drops the patch, and the minor too once the major version reached one") {
    assertEquals(Platform.toBinaryVersion("1.19.0"), "1")
    assertEquals(Platform.toBinaryVersion("0.5.8"), "0.5")
    assertEquals(Platform.toBinaryVersion("0.4.17"), "0.4")
    assertEquals(Platform.toBinaryVersion("2"), "2")
  }
}
