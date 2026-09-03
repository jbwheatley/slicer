package spec.modern

type NamedTuple = (name: String, age: Int)

object NamedTuples {
  val person: NamedTuple = (name = "ada", age = 36)

  def nameOf(value: NamedTuple): String = value.name

  def older(value: NamedTuple, years: Int): NamedTuple = (name = value.name, age = value.age + years)
}

object NamedContextBounds {
  def largest[A: Ordering as ordering](values: List[A]): A = values.max(using ordering)

  def sorted[A: Ordering](values: List[A]): List[A] = values.sorted
}

object NewGivens {
  trait Label[A] {
    def label(value: A): String
  }

  given Label[Int] = (value: Int) => value.toString

  given longLabel: Label[Long] = (value: Long) => value.toString + "L"

  def labelled[A](value: A)(using labeller: Label[A]): String = labeller.label(value)
}
