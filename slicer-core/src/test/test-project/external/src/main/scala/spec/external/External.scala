package spec.external

import cats.Semigroup
import cats.data.{NonEmptyList, Validated}
import cats.syntax.all.*

case class HasLibraryGiven(name: String, parts: List[String])

object HasLibraryGiven {
  given Semigroup[HasLibraryGiven] with {
    def combine(left: HasLibraryGiven, right: HasLibraryGiven): HasLibraryGiven =
      HasLibraryGiven(left.name, left.parts ++ right.parts)
  }
}

object CallsLibrary {
  type LibraryAlias[A] = Validated[NonEmptyList[String], A]

  def checksName(value: HasLibraryGiven): LibraryAlias[String] =
    Validated.condNel(value.name.nonEmpty, value.name, "empty name")

  def checksParts(value: HasLibraryGiven): LibraryAlias[List[String]] =
    value.parts.traverse(part => Validated.condNel(part.nonEmpty, part, s"empty part in ${value.name}"))

  def combinesChecks(value: HasLibraryGiven): LibraryAlias[HasLibraryGiven] =
    (checksName(value), checksParts(value)).mapN(HasLibraryGiven.apply)

  def reducesWithLibraryGiven(values: NonEmptyList[HasLibraryGiven]): HasLibraryGiven = values.reduce
}
