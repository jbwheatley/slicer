package spec.implicitclasses

object StringSyntax {
  implicit class ImplicitClassOnString(private val value: String) extends AnyVal {
    def shouted: String = value.toUpperCase + "!"

    def repeatedTwice: String = value + value
  }

  implicit class ImplicitClassOnList[A](values: List[A]) {
    def secondOption: Option[A] = values.drop(1).headOption

    def renderedWith(render: A => String): String = values.map(render).mkString("/")
  }
}

object CallsImplicitClasses {
  import StringSyntax._

  def callsValueClassSyntax(value: String): String = value.shouted

  def callsGenericSyntax(values: List[Int]): String = values.renderedWith(_.toString)

  def callsSecondOption(values: List[Int]): Option[Int] = values.secondOption
}

class ValueClassWrapper(val underlying: Long) extends AnyVal {
  def doubled: Long = underlying * 2
}

object CallsValueClass {
  def call(value: Long): Long = new ValueClassWrapper(value).doubled
}
