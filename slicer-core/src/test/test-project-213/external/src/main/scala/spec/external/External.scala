package spec.external

import cats.Semigroup
import cats.data.NonEmptyList
import cats.data.Validated
import cats.syntax.all._

case class HasLibraryImplicit(name: String, parts: List[String])

object HasLibraryImplicit {
  implicit val semigroupForLibraryValue: Semigroup[HasLibraryImplicit] =
    new Semigroup[HasLibraryImplicit] {
      def combine(left: HasLibraryImplicit, right: HasLibraryImplicit): HasLibraryImplicit =
        HasLibraryImplicit(left.name, left.parts ++ right.parts)
    }
}

object CallsLibrary {
  type LibraryAlias[A] = Validated[NonEmptyList[String], A]

  def checksName(value: HasLibraryImplicit): LibraryAlias[String] =
    Validated.condNel(value.name.nonEmpty, value.name, "empty name")

  def checksParts(value: HasLibraryImplicit): LibraryAlias[List[String]] =
    value.parts.traverse(part => Validated.condNel(part.nonEmpty, part, s"empty part in ${value.name}"))

  def combinesChecks(value: HasLibraryImplicit): LibraryAlias[HasLibraryImplicit] =
    (checksName(value), checksParts(value)).mapN(HasLibraryImplicit.apply)

  def reducesWithLibraryImplicit(values: NonEmptyList[HasLibraryImplicit]): HasLibraryImplicit = values.reduce
}
