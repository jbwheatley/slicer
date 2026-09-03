package spec.jsbase

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportTopLevel, JSGlobal}

@js.native
@JSGlobal("JSON")
object Json extends js.Object {
  def stringify(value: js.Any): String = js.native
}

final case class Reading(label: String, value: Double)

object Readings {

  def describe(reading: Reading): String =
    Json.stringify(js.Dictionary(reading.label -> reading.value))

  def loudly(reading: Reading): String = describe(reading).toUpperCase

  @JSExportTopLevel("describeReading")
  def exported(label: String, value: Double): String = describe(Reading(label, value))
}
