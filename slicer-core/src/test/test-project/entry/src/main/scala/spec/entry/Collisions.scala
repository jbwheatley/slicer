package spec.entry

trait DerivableTypeClass[A] {
  def label(a: A): String
}

object DerivableTypeClass {
  given DerivableTypeClass[Boolean] = value => if (value) "yes" else "no"
}

case class CollidesWithBaseName(flag: Boolean) derives spec.derivation.DerivableTypeClass

object CallsCollidingNames {
  def callsLocalName(value: CollidesWithBaseName): String = summon[DerivableTypeClass[Boolean]].label(value.flag)

  def callsQualifiedName(value: CollidesWithBaseName): String =
    spec.derivation.DerivableTypeClass.of[CollidesWithBaseName].label(value)
}
