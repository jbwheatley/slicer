package spec.jsexternal

import spec.jsbase.{Reading, Readings}

import cats.syntax.foldable.*

object Summary {

  def summarise(readings: List[Reading]): String =
    readings.map(Readings.describe).intercalate(", ")

  def loudest(readings: List[Reading]): String =
    readings.maxByOption(_.value).map(Readings.loudly).getOrElse("")
}
