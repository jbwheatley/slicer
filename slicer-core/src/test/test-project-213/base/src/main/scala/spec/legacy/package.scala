package spec

package object legacy {
  type LegacyAlias = Map[String, String]

  val valueInPackageObject: String = "package object"

  implicit val implicitInPackageObject: String = "implicit"

  def definitionInPackageObject(implicit value: String): String = s"$valueInPackageObject:$value"

  def readsAlias(entries: LegacyAlias): String = entries.keys.toList.sorted.mkString(",")
}
