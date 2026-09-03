package spec.nativeexternal

import spec.nativebase.{Buffers, Sample}

import cats.syntax.foldable.*

object Summary {

  def summarise(samples: List[Sample]): String =
    samples.map(Buffers.describe).intercalate(", ")

  def loudest(samples: List[Sample]): String =
    samples.maxByOption(_.value).map(Buffers.loudly).getOrElse("")
}
