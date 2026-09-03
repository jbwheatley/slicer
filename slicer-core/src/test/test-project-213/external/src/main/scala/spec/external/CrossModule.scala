package spec.external

import spec.implicits.{HasImplicitsInCompanion => RenamedImport}
import spec.implicits.PriorityTypeClass
import spec.implicits.TypeClass
import spec.members.OverloadedMembers

case class DefinedInMiddleModule(title: String, rows: List[String])

object CallsAcrossModules {
  def callsOverloadedInBase(value: DefinedInMiddleModule): String = OverloadedMembers.overloaded(value.title)

  def summonsThroughRenamedImport(value: RenamedImport): String = implicitly[TypeClass[RenamedImport]].render(value)

  def summonsPriorityInstance(value: Int): String = implicitly[PriorityTypeClass[Int]].encode(value)

  def mutuallyRecursive(count: Int): Int = if (count <= 0) 0 else mutuallyRecursiveHelper(count - 1)

  private def mutuallyRecursiveHelper(count: Int): Int = if (count <= 0) 1 else mutuallyRecursive(count - 1)
}
