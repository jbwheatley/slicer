package spec.abstracttypes

trait HasTwoTypeMembers {
  type UsedType
  type UnusedType
  def reads(key: UsedType): String
  def writes(value: UnusedType): String
}

object BindsTypeMembers extends HasTwoTypeMembers {
  type UsedType = String
  type UnusedType = Int

  def reads(key: UsedType): String = key.reverse

  def writes(value: UnusedType): String = value.toString
}

object CallsUsedTypeMember {
  def calls(source: HasTwoTypeMembers, key: source.UsedType): String = source.reads(key)
}
