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

import cats.Eq

private[slicer] enum DefKind(val keyword: String) {
  case Class extends DefKind("class")
  case Trait extends DefKind("trait")
  case Object extends DefKind("object")
  case Enum extends DefKind("enum")
  case EnumCase extends DefKind("case")
  case Def extends DefKind("def")
  case Val extends DefKind("val")
  case Var extends DefKind("var")
  case Type extends DefKind("type")
  case Given extends DefKind("given")
  case Param extends DefKind("param")
  case Extension extends DefKind("extension")
  case Binding extends DefKind("val")
  case JavaType extends DefKind("class")

  def isContainer: Boolean = DefKind.containers.contains(this)
}

private[slicer] object DefKind {

  given Eq[DefKind] = Eq.fromUniversalEquals

  private val containers: Set[DefKind] = Set(Class, Trait, Object, Enum)

  val searchableByKeyword: Map[String, DefKind] =
    Vector(Class, Trait, Object, Def, Given, Val, Var).map(kind => kind.keyword -> kind).toMap
}
