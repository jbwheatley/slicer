package spec.opaques

opaque type OpaqueType = Long

object OpaqueType {
  def apply(raw: Long): OpaqueType = raw

  def zero: OpaqueType = 0L

  extension (value: OpaqueType) {
    def toLong: Long = value
    def plus(other: OpaqueType): OpaqueType = value + other
    def formatted: String = s"${value / 100}.${value % 100}"
  }
}

type AliasOfOpaqueType = OpaqueType

object CallsOpaqueType {
  import OpaqueType.*

  def folds(values: List[OpaqueType]): OpaqueType = values.foldLeft(OpaqueType.zero)(_ plus _)

  def formats(value: AliasOfOpaqueType): String = value.formatted
}

extension (value: String) {
  def usedExtensionMethod: String = value.toUpperCase + "!"
  def unusedExtensionMethod: String = value.split(" ").map(_.take(1)).mkString
}

object CallsExtensionMethod {
  def calls(value: String): String = value.usedExtensionMethod
}
