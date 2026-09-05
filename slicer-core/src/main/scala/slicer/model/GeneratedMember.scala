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
import cats.syntax.eq.*

private[slicer] enum GeneratedMember(val memberName: String) {
  case Apply extends GeneratedMember("apply")
  case Unapply extends GeneratedMember("unapply")
  case UnapplySeq extends GeneratedMember("unapplySeq")
  case UnapplyVector extends GeneratedMember("unapplyVector")
  case Copy extends GeneratedMember("copy")
  case Derived extends GeneratedMember("derived")
}

private[slicer] object GeneratedMember {

  given Eq[GeneratedMember] = Eq.fromUniversalEquals

  val constructionEntries: Vector[GeneratedMember] = Vector(Apply, Unapply, UnapplyVector, Copy)

  val factoryEntries: Vector[GeneratedMember] = Vector(Apply, Unapply, UnapplySeq, UnapplyVector)

  def isFactoryEntry(memberName: String): Boolean = factoryEntries.exists(_.memberName === memberName)

  def isDerivation(memberName: String): Boolean = memberName === Derived.memberName
}
