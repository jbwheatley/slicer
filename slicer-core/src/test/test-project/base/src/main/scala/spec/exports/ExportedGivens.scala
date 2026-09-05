package spec.exports

trait ExportedTypeClass[A] {
  def encode(a: A): String
}

object ExportedInstances {
  given exportedIntInstance: ExportedTypeClass[Int] = value => s"exported:$value"

  given exportedStringInstance: ExportedTypeClass[String] = identity
}

object ExportsGivenInstances {
  export ExportedInstances.given
}

object CallsExportedGiven {
  import ExportsGivenInstances.given

  def encodesInt(value: Int): String = summon[ExportedTypeClass[Int]].encode(value)
}
