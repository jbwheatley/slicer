package spec.derivation

import scala.deriving.Mirror

trait DerivableTypeClass[A] {
  def label(a: A): String
}

object DerivableTypeClass {
  given DerivableTypeClass[Int] = value => s"int:$value"
  given DerivableTypeClass[String] = value => s"str:$value"

  def of[A](using instance: DerivableTypeClass[A]): DerivableTypeClass[A] = instance

  def derived[A]: DerivableTypeClass[A] = a => a.toString
}

case class DerivesTypeClass(name: String) derives DerivableTypeClass

object CallsDerivedInstance {
  def rendersDerived(value: DerivesTypeClass): String = DerivableTypeClass.of[DerivesTypeClass].label(value)

  def rendersGiven(value: Int): String = DerivableTypeClass.of[Int].label(value)
}

object HasGivenOrdering {
  given ordering: Ordering[DerivesTypeClass] = Ordering.by(_.name)

  def sorts(values: List[DerivesTypeClass]): List[DerivesTypeClass] = values.sorted
}

object ImportsGivens {
  import DerivableTypeClass.given

  def summonsBoth(int: Int, string: String): String =
    summon[DerivableTypeClass[Int]].label(int) + summon[DerivableTypeClass[String]].label(string)
}

case class GenericDerivesTypeClass[A](value: A, tag: String) derives DerivableTypeClass

object GenericDerivesTypeClass {
  def of[A](value: A): GenericDerivesTypeClass[A] = GenericDerivesTypeClass(value, "auto")
}

enum EnumDerivesTypeClass[+A] derives DerivableTypeClass {
  case Node(value: A, children: List[EnumDerivesTypeClass[A]])
}

object CallsEnumDerivedInstance {
  def counts[A](value: EnumDerivesTypeClass[A]): Int = value match {
    case EnumDerivesTypeClass.Node(_, children) => 1 + children.map(counts).sum
  }

  def renders(value: EnumDerivesTypeClass[Int]): String =
    DerivableTypeClass.of[EnumDerivesTypeClass[Int]].label(value)
}

object UsesMirror {
  inline def fieldNames[A](using mirror: Mirror.ProductOf[A]): List[String] =
    scala.compiletime.constValueTuple[mirror.MirroredElemLabels].toList.map(_.toString)

  inline def fieldNamesOfDerivesTypeClass: List[String] = fieldNames[DerivesTypeClass]
}
