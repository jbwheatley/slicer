package spec.imports

object ImportedMembers {
  val usedConstant: Int = 1

  val unusedConstant: Int = 2

  def usedMember(value: Int): Int = value + usedConstant
}
