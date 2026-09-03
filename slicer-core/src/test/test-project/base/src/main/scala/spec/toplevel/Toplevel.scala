package spec.toplevel

val topLevelValue: String = " | "

def topLevelDefinition(parts: List[String]): String = parts.mkString(topLevelValue)

extension (context: StringContext) {
  def interpolator(args: Any*): String = context.s(args*).toUpperCase
}

object CallsInterpolator {
  def calls(title: String): String = interpolator"interpolated $title"
}

object CallsTopLevelDefinition {
  def calls(parts: List[String]): String = topLevelDefinition(parts)
}

class HasUnapplyInCompanion[A](val value: A) {
  def map[B](f: A => B): HasUnapplyInCompanion[B] = new HasUnapplyInCompanion(f(value))
}

object HasUnapplyInCompanion {
  def unapply[A](value: HasUnapplyInCompanion[A]): Option[A] = Some(value.value)

  def pure[A](a: A): HasUnapplyInCompanion[A] = new HasUnapplyInCompanion(a)
}

object CallsUnapply {
  def matchesWithUnapply(wrapper: HasUnapplyInCompanion[String]): String = wrapper match {
    case HasUnapplyInCompanion(value) => value
  }

  def callsFactoryAndMap(value: String): HasUnapplyInCompanion[Int] = HasUnapplyInCompanion.pure(value).map(_.length)
}

@main def topLevelMain(): Unit = println(topLevelDefinition(List("a", "b")))
