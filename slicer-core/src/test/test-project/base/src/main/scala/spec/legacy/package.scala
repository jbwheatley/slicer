package spec

package object legacy {
  val valueInPackageObject: String = "package object"

  implicit val implicitInPackageObject: String = "implicit"

  def definitionInPackageObject(implicit value: String): String = s"$valueInPackageObject:$value"
}
