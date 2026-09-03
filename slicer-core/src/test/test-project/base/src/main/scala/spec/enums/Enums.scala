package spec.enums

enum SimpleEnum {
  case WithoutParameters
  case WithOneParameter(reason: String)
  case WithTwoParameters(at: Long, reason: String)
}

enum ParameterizedEnum(val weight: Int) {
  case Light extends ParameterizedEnum(1)
  case Heavy extends ParameterizedEnum(10)

  def heavier: ParameterizedEnum = this match {
    case Light => Heavy
    case Heavy => Heavy
  }
}

object ParameterizedEnum {
  def heaviest(values: List[ParameterizedEnum]): ParameterizedEnum = values.maxBy(_.weight)
}

enum GenericEnum[+A] {
  case Leaf(value: A)
  case Branch(left: GenericEnum[A], right: GenericEnum[A])
}

sealed trait SealedTrait
final case class SealedCaseClass(id: Long) extends SealedTrait
final case class SealedCaseClassWithTwoFields(id: Long, name: String) extends SealedTrait
case object SealedCaseObject extends SealedTrait

object MatchesEnums {
  def matchesSealedTrait(value: SealedTrait): String = value match {
    case SealedCaseClass(id)                    => s"one $id"
    case SealedCaseClassWithTwoFields(id, name) => s"two $id $name"
    case SealedCaseObject                       => "object"
  }

  def matchesSimpleEnum(value: SimpleEnum): String = value match {
    case SimpleEnum.WithoutParameters            => "none"
    case SimpleEnum.WithOneParameter(reason)     => s"one: $reason"
    case SimpleEnum.WithTwoParameters(at, reason) => s"two: $at $reason"
  }

  def collectsOneCase(values: List[SealedTrait]): List[Long] = values.collect { case SealedCaseClass(id) => id }

  def recursesOverGenericEnum[A](value: GenericEnum[A]): Int = value match {
    case GenericEnum.Leaf(_)         => 1
    case GenericEnum.Branch(left, right) => recursesOverGenericEnum(left) + recursesOverGenericEnum(right)
  }
}
