package spec.external

import spec.derivation.{DerivableTypeClass, DerivesTypeClass}
import spec.givens.PriorityTypeClass
import spec.givens.HasGivensInCompanion as RenamedImport
import spec.members.OverloadedMembers

case class DefinedInMiddleModule(title: String, rows: List[String])

object CallsAcrossModules {
  def callsOverloadedInBase(value: DefinedInMiddleModule): String = OverloadedMembers.overloaded(value.title)

  def summonsTypeClassFromBase(value: DerivesTypeClass): String = DerivableTypeClass.of[DerivesTypeClass].label(value)

  def summonsThroughRenamedImport(value: RenamedImport): String =
    summon[spec.givens.TypeClass[RenamedImport]].render(value)

  def summonsPriorityInstance(value: Int): String = summon[PriorityTypeClass[Int]].encode(value)

  def mutuallyRecursive(count: Int): Int = if (count <= 0) 0 else mutuallyRecursiveHelper(count - 1)

  private def mutuallyRecursiveHelper(count: Int): Int = if (count <= 0) 1 else mutuallyRecursive(count - 1)
}
