package spec.entry

import cats.data.NonEmptyList

import spec.enums.{MatchesEnums, ParameterizedEnum, SimpleEnum}
import spec.external.{CallsAcrossModules, CallsLibrary, DefinedInMiddleModule, HasLibraryGiven}
import spec.givens.PriorityTypeClass as RenamedTypeClass

object UsesLibraryModule {
  def callsMiddleModule(title: String): String =
    CallsAcrossModules.callsOverloadedInBase(DefinedInMiddleModule(title, Nil))

  def reducesLibraryValues(values: NonEmptyList[HasLibraryGiven]): String =
    CallsLibrary.reducesWithLibraryGiven(values).name

  def summonsThroughRenamedImport(value: Int): String = summon[RenamedTypeClass[Int]].encode(value)

  def readsEnumWeight(value: ParameterizedEnum): Int = value.heavier.weight

  def matchesEnum(value: SimpleEnum): String = MatchesEnums.matchesSimpleEnum(value)
}
