package spec.nativebase

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

final case class Sample(label: String, value: Int)

object Buffers {

  def describe(sample: Sample): String = s"${sample.label}=${sample.value}"

  def loudly(sample: Sample): String = describe(sample).toUpperCase

  def sized(sample: Sample): CSize = describe(sample).length.toCSize
}
