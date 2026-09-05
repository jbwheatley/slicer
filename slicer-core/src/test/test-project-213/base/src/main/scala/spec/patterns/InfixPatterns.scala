package spec.patterns

object Joined {
  def unapply(value: String): Option[(String, String)] = value.split(':') match {
    case Array(head, tail) => Some((head, tail))
    case _                 => None
  }
}

object MatchesInfixExtractor {
  def matchesInfix(value: String): String = value match {
    case head Joined tail => head + tail
    case _                => value
  }
}
