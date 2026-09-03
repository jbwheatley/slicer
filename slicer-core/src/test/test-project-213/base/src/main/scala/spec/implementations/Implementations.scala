package spec.implementations

trait AbstractSource {
  def rows: List[Map[String, String]]
}

class SourceImplementation extends AbstractSource {
  private val storedRows = List(Map("key" -> "1", "value" -> "one"))

  def rows: List[Map[String, String]] = storedRows
}

trait AbstractWithTwoMembers {
  def calledMember(key: Long): Option[String]
  def uncalledMember(): List[String]
}

class DirectImplementation(source: AbstractSource) extends AbstractWithTwoMembers {
  def calledMember(key: Long): Option[String] = source.rows.find(_("key") == key.toString).map(toValue)

  def uncalledMember(): List[String] = source.rows.map(toValue)

  private def toValue(row: Map[String, String]): String = row("value")
}

class DelegatingImplementation(underlying: AbstractWithTwoMembers) extends AbstractWithTwoMembers {
  private val cachedValues = scala.collection.mutable.Map.empty[Long, Option[String]]

  def calledMember(key: Long): Option[String] = cachedValues.getOrElseUpdate(key, underlying.calledMember(key))

  def uncalledMember(): List[String] = underlying.uncalledMember()
}
