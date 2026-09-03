package spec.conversions

import scala.language.implicitConversions

case class WrappedIdentifier(value: Long)

object WrappedIdentifier {
  implicit def wrapsLong(value: Long): WrappedIdentifier = WrappedIdentifier(value)

  implicit def unwrapsIdentifier(identifier: WrappedIdentifier): Long = identifier.value
}

object ConversionsInScope {
  implicit def convertsStringToLength(value: String): Int = value.length

  implicit def convertsListToLabel(values: List[String]): String = values.mkString(",")

  def unusedConversionTarget(value: Int): String = value.toString
}

object CallsConversions {
  import ConversionsInScope._
  import WrappedIdentifier._

  def convertsImplicitly(value: String): Int = value

  def convertsThroughCompanion(value: Long): WrappedIdentifier = value

  def convertsBack(identifier: WrappedIdentifier): Long = identifier

  def convertsList(values: List[String]): Int = convertsStringToLength(values)
}
