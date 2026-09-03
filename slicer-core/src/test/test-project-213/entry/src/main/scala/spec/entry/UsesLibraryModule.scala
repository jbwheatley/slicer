package spec.entry

import cats.data.NonEmptyList

import spec.external.{CallsAcrossModules, CallsLibrary, DefinedInMiddleModule, HasLibraryImplicit}
import spec.implicits.{PriorityTypeClass => RenamedTypeClass}
import spec.patterns.{MatchesSealedShape, SealedShape}

object UsesLibraryModule {
  def callsMiddleModule(title: String): String =
    CallsAcrossModules.callsOverloadedInBase(DefinedInMiddleModule(title, Nil))

  def reducesLibraryValues(values: NonEmptyList[HasLibraryImplicit]): String =
    CallsLibrary.reducesWithLibraryImplicit(values).name

  def summonsThroughRenamedImport(value: Int): String = implicitly[RenamedTypeClass[Int]].encode(value)

  def matchesShape(shape: SealedShape): Int = MatchesSealedShape.area(shape)
}
