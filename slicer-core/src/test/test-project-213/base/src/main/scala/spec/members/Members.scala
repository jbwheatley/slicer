package spec.members

trait AbstractAndConcrete {
  def abstractMember: String
  def concreteMember: String = abstractMember + " (concrete)"
}

case class ImplementsAsCaseClass(abstractMember: String, unusedField: String) extends AbstractAndConcrete

class OverridesConcreteMember(val abstractMember: String, val page: Int) extends AbstractAndConcrete {
  def this(abstractMember: String) = this(abstractMember, 1)

  override def concreteMember: String = s"$abstractMember @ $page"
}

object CallsConcreteMember {
  def viaTrait(value: AbstractAndConcrete): String = value.concreteMember

  def viaCaseClass: String = viaTrait(ImplementsAsCaseClass("a", "b"))

  def viaAuxiliaryConstructor: String = viaTrait(new OverridesConcreteMember("c"))
}

trait RecursivelyBounded[A <: RecursivelyBounded[A]] {
  def self: A
  def pairedWith(other: A): List[A] = List(self, other)
}

class ImplementsRecursiveBound(val id: Int) extends RecursivelyBounded[ImplementsRecursiveBound] {
  def self: ImplementsRecursiveBound = this
}

object CallsRecursiveBound {
  def pair: List[ImplementsRecursiveBound] =
    new ImplementsRecursiveBound(1).pairedWith(new ImplementsRecursiveBound(2))
}

class OperatorMember(val amount: Long) {
  def +(other: OperatorMember): OperatorMember = new OperatorMember(amount + other.amount)

  def ::(other: OperatorMember): List[OperatorMember] = List(other, this)
}

object CallsOperatorMember {
  def sum(left: OperatorMember, right: OperatorMember): OperatorMember = left + right

  def rightAssociative(left: OperatorMember, right: OperatorMember): List[OperatorMember] = left :: right
}

object OverloadedMembers {
  def overloaded(value: Int): String = value.toString

  def overloaded(value: String): String = value

  def overloaded(value: String, repeats: Int): String = List.fill(repeats)(value).mkString

  def withDefaultSeparator(separator: String = ", ")(parts: String*): String = parts.mkString(separator)

  def withByNameParameter(compute: => String): String = compute + compute

  def callsAll(): String =
    overloaded(1) + overloaded("a") + overloaded("b", 2) + withDefaultSeparator("-")("x", "y") +
      withByNameParameter("z")
}

object ParameterForms {
  def curried(left: Int)(right: Int): Int = left + right

  def partiallyApplied: Int => Int = curried(5)

  def callsPartiallyApplied: Int = applyTwice(partiallyApplied, 1)

  def applyTwice(f: Int => Int, value: Int): Int = f(f(value))

  def withDefaults(name: String, greeting: String = "hello", punctuation: String = "!"): String =
    s"$greeting $name$punctuation"

  def callsWithNamedArguments: String = withDefaults(greeting = "good day", name = "ada")

  def etaExpanded: List[String] = List(1, 2).map(OverloadedMembers.overloaded)
}
