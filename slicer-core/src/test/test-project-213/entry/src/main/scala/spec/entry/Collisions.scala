package spec.entry

trait TypeClass[A] {
  def label(a: A): String
}

object TypeClass {
  implicit val typeClassForBoolean: TypeClass[Boolean] = new TypeClass[Boolean] {
    def label(a: Boolean): String = if (a) "yes" else "no"
  }
}

case class CollidesWithBaseName(flag: Boolean)

object CallsCollidingNames {
  def callsLocalName(value: CollidesWithBaseName): String = implicitly[TypeClass[Boolean]].label(value.flag)

  def callsQualifiedName(value: CollidesWithBaseName): String =
    implicitly[spec.implicits.TypeClass[String]].render(value.flag.toString)
}
