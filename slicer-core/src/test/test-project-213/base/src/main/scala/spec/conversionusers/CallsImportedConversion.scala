package spec.conversionusers

import scala.language.implicitConversions

import spec.conversions.ConversionsInScope._

object CallsImportedConversion {
  def convertsAcrossPackages(value: String): Int = value
}
