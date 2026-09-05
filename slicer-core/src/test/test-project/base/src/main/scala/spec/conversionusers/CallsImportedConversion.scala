package spec.conversionusers

import spec.conversions.WrappedLength
import spec.conversions.LengthConversions.given

object CallsImportedConversion {
  def convertsAcrossPackages(value: String): Long = value

  def wrapsAcrossPackages(value: Long): WrappedLength = value
}
