package spec.implicits

trait TypeClass[A] {
  def render(a: A): String
}

object TypeClass {
  implicit val typeClassForInt: TypeClass[Int] = new TypeClass[Int] {
    def render(a: Int): String = a.toString
  }

  implicit val typeClassForString: TypeClass[String] = new TypeClass[String] {
    def render(a: String): String = a
  }

  implicit def typeClassForList[A](implicit inner: TypeClass[A]): TypeClass[List[A]] =
    new TypeClass[List[A]] {
      def render(a: List[A]): String = a.map(inner.render).mkString("[", ", ", "]")
    }

  implicit def typeClassForPair[A, B](implicit left: TypeClass[A], right: TypeClass[B]): TypeClass[(A, B)] =
    new TypeClass[(A, B)] {
      def render(a: (A, B)): String = s"(${left.render(a._1)}, ${right.render(a._2)})"
    }

  def renderWithContextBound[A: TypeClass](a: A): String = implicitly[TypeClass[A]].render(a)
}

object CallsTypeClass {
  def rendersList(values: List[Int]): String = TypeClass.renderWithContextBound(values)

  def rendersPair(pair: (Int, String)): String = TypeClass.renderWithContextBound(pair)
}

trait PriorityTypeClass[A] {
  def encode(a: A): String
}

trait LowPriorityInstances {
  implicit def fallbackInstance[A]: PriorityTypeClass[A] = new PriorityTypeClass[A] {
    def encode(a: A): String = a.toString
  }
}

object PriorityTypeClass extends LowPriorityInstances {
  implicit val specificInstance: PriorityTypeClass[Int] = new PriorityTypeClass[Int] {
    def encode(a: Int): String = s"i$a"
  }
}

object CallsPriorityTypeClass {
  def encodeWithImplicit[A](a: A)(implicit instance: PriorityTypeClass[A]): String = instance.encode(a)

  def picksSpecificInstance: String = encodeWithImplicit(1)

  def picksFallbackInstance: String = encodeWithImplicit(1.5)
}

case class HasImplicitsInCompanion(code: String)

object HasImplicitsInCompanion {
  implicit val typeClassForCompanion: TypeClass[HasImplicitsInCompanion] = new TypeClass[HasImplicitsInCompanion] {
    def render(a: HasImplicitsInCompanion): String = s"companion:${a.code}"
  }

  implicit val orderingForCompanion: Ordering[HasImplicitsInCompanion] = Ordering.by(_.code)
}

object CallsImplicitsInCompanionScope {
  def rendersFromCompanion(value: HasImplicitsInCompanion): String =
    implicitly[TypeClass[HasImplicitsInCompanion]].render(value)

  def sortsWithCompanionOrdering(values: List[HasImplicitsInCompanion]): List[HasImplicitsInCompanion] = values.sorted
}

object OldStyleImplicitParameters {
  implicit val implicitValue: String = "implicit"

  def takesImplicitParameter(implicit value: String): String = s"implicit:$value"

  def callsWithImplicitParameter: String = takesImplicitParameter

  def callsWithExplicitArgument: String = takesImplicitParameter("explicit")
}
