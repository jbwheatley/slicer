package spec.patterns

sealed trait SealedShape

case class ShapeWithFields(width: Int, height: Int) extends SealedShape

case class ShapeWithOneField(radius: Int) extends SealedShape

case object ShapeWithoutFields extends SealedShape

object MatchesSealedShape {
  def area(shape: SealedShape): Int = shape match {
    case ShapeWithFields(width, height) => width * height
    case ShapeWithOneField(radius)      => radius * radius * 3
    case ShapeWithoutFields             => 0
  }

  def matchesWithGuard(shape: SealedShape): String = shape match {
    case ShapeWithFields(width, _) if width > 10 => "wide"
    case other                                   => other.toString
  }
}

object CustomExtractor {
  def unapply(value: String): Option[(String, String)] =
    value.split(':') match {
      case Array(head, tail) => Some((head, tail))
      case _                 => None
    }
}

object BooleanExtractor {
  def unapply(value: Int): Boolean = value % 2 == 0
}

object SequenceExtractor {
  def unapplySeq(value: String): Option[Seq[String]] = Some(value.split(',').toIndexedSeq)
}

object CallsExtractors {
  def callsCustomExtractor(value: String): String = value match {
    case CustomExtractor(head, tail) => s"$head/$tail"
    case _                           => value
  }

  def callsBooleanExtractor(value: Int): String = value match {
    case BooleanExtractor() => "even"
    case _                  => "odd"
  }

  def callsSequenceExtractor(value: String): String = value match {
    case SequenceExtractor(first, _*) => first
    case _                            => value
  }

  def destructuresInForComprehension(values: List[ShapeWithFields]): List[Int] =
    for {
      ShapeWithFields(width, height) <- values
      if width > 0
    } yield width * height

  def matchesPartialFunction: PartialFunction[SealedShape, String] = { case ShapeWithOneField(radius) =>
    radius.toString
  }
}
