package spec.interpolators

object PathInterpolator {

  implicit class SegmentContext(context: StringContext) {

    object segment {
      def unapplySeq(value: String): Option[Seq[String]] =
        Option.when(value.startsWith(context.parts.head))(
          value.stripPrefix(context.parts.head).split('/').toIndexedSeq
        )
    }
  }
}

object MatchesInterpolatedPattern {
  import PathInterpolator.SegmentContext

  def matchesSegments(value: String): String = value match {
    case segment"/$head/$tail" => head + tail
    case _                     => value
  }
}
