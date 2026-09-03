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
import cats.Order
import cats.syntax.eq.*

private[slicer] opaque type Symbol = String

private[slicer] object Symbol {

  def apply(value: String): Symbol = value

  def synthetic(label: String): Symbol = syntheticPrefix + label

  private val syntheticPrefix = "slice-synthetic:"

  given Eq[Symbol] = Eq.fromUniversalEquals

  given Order[Symbol] = Order.by(_.value)

  given Ordering[Symbol] = Ordering.by(_.value)

  def takeLastSegment(name: String): String = name.split('.').lastOption.getOrElse(name)

  extension (symbol: Symbol) {
    def value: String = symbol

    def isSynthetic: Boolean = symbol.startsWith(syntheticPrefix)

    def toDottedName: String =
      if (symbol.isSynthetic) symbol.value
      else
        symbol.value
          .stripSuffix(".")
          .stripSuffix("#")
          .replace("().", ".")
          .replace("()", "")
          .replace('/', '.')
          .replace('#', '.')

    def toDisplayName: String = takeLastSegment(symbol.toDottedName)

    def toQualifiedName: String = symbol.toDottedName.split('.').takeRight(2).mkString(".")

    def toOwnerName: String = symbol.toDottedName.split('.').dropRight(1).lastOption.getOrElse(symbol.value)

    def isLocalSymbol: Boolean = symbol.value.startsWith("local")

    def toDirectoryName: String = symbol.value.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "")

    def isConstructorSymbol: Boolean = isConstructor(symbol.value)

    def toPackagePrefix: String =
      if (symbol.isSynthetic) ""
      else {
        val text = symbol.value
        val cut = text.indexWhere(c => c === '#' || c === '(')
        val head = if (cut >= 0) text.substring(0, cut) else text
        val slash = head.lastIndexOf('/')
        if (slash >= 0) head.substring(0, slash + 1) else ""
      }

    def withCompanionSymbol: Set[Symbol] =
      if (symbol.value.endsWith("#")) Set(symbol, Symbol(symbol.value.dropRight(1) + "."))
      else
        symbol.findCompanionClass match {
          case Some(cls) => Set(symbol, cls)
          case None      => Set(symbol)
        }

    def findCompanionClass: Option[Symbol] =
      if (symbol.value.endsWith(".") && !symbol.value.endsWith(").")) Some(Symbol(symbol.value.dropRight(1) + "#"))
      else None

    def findParameterFixingOwner: Option[Symbol] =
      findConstructorOwner(symbol.value).orElse(symbol.value match {
        case CompanionEntry(owner, _) => Some(Symbol(owner + "#"))
        case _                        => None
      })

    def findGetterForSetter: Option[Symbol] = symbol.value match {
      case Setter(owner, name) => Some(Symbol(s"$owner$name()."))
      case _                   => None
    }

    def findOwnerSymbol: Option[Symbol] =
      if (symbol.isSynthetic) None
      else
        LastDescriptor
          .findFirstMatchIn(symbol.value)
          .map(matched => symbol.value.substring(0, matched.start))
          .filter(_.nonEmpty)
          .map(Symbol.apply)

    def isUniversalMember: Boolean = universalOwners.exists(symbol.value.startsWith)
  }

  private def isConstructor(sym: String): Boolean = sym.contains("<init>")

  private def findConstructorOwner(sym: String): Option[Symbol] = {
    val hash = sym.indexOf('#')
    if (isConstructor(sym) && hash >= 0) Some(Symbol(sym.substring(0, hash + 1))) else None
  }

  private lazy val CompanionEntry =
    s"""(.*)\\.(${GeneratedMember.constructionEntries.map(_.memberName).mkString("|")})\\(\\)\\.$$""".r

  private lazy val Setter = """^(.*?)`?([^/#.`]+)_=`?\(\)\.$""".r

  private lazy val LastDescriptor = """[^/#.()]*(?:\([^()]*\))?[.#]$|\([^()]*\)$""".r

  private val universalOwners = Set("java/lang/Object#", "scala/Any#", "scala/AnyRef#")
}
