package spec.access

object PrivateMembers {
  private def privateDefinition: String = "private"

  def callsPrivateDefinition: String = privateDefinition

  private[spec] def packagePrivateDefinition: String = "package private"

  def callsPackagePrivateDefinition: String = packagePrivateDefinition
}

class PrivateConstructor private (val field: String) {
  def readsField(key: String): Option[String] = if (key == field) Some(field) else None
}

object PrivateConstructor {
  def empty: PrivateConstructor = new PrivateConstructor("")

  def of(field: String): PrivateConstructor = new PrivateConstructor(field)
}

object CallsPrivateConstructor {
  def call(key: String): Option[String] = PrivateConstructor.of("a").readsField(key)
}

class HasPrivateThisField(name: String) {
  private[this] val privateThisField: String = name.toUpperCase

  def readsPrivateThisField: String = privateThisField
}

class HasPrivateNestedObject(val outerField: String) {
  private object NestedObject {
    def nestedDefinition: String = s"[$outerField]"
  }

  def callsNestedDefinition: String = NestedObject.nestedDefinition + outerField
}

object CallsPrivateNestedObject {
  def call: String = new HasPrivateNestedObject("x").callsNestedDefinition
}

class HasProtectedMember {
  protected def protectedDefinition: String = "protected"
}

class OverridesProtectedMember extends HasProtectedMember {
  override protected def protectedDefinition: String = "overridden"

  def callsProtectedDefinition: String = protectedDefinition
}
