package spec.givenparents

trait Describer[A] {
  def describe(a: A): String
  def describeAll(values: List[A]): String = values.map(describe).mkString(",")
}

object DescriberInstances {
  given intDescriber: Describer[Int] with {
    def describe(a: Int): String = a.toString
  }
}

class DescribesLongs extends Describer[Long] {
  def describe(a: Long): String = a.toString
}
