package spec.conversions

case class WrappedLength(value: Long)

object LengthConversions {
  given lengthOfString: Conversion[String, Long] = _.length

  given wrapsLength: Conversion[Long, WrappedLength] with {
    def apply(value: Long): WrappedLength = WrappedLength(value)
  }
}
