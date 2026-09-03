package spec.patterns

case class HasCopyAndDefault(host: String, port: Int, debug: Boolean = false)

object CallsCopy {
  def callsCopy(value: HasCopyAndDefault, port: Int): HasCopyAndDefault = value.copy(port = port)

  def callsApplyWithDefault: HasCopyAndDefault = HasCopyAndDefault("host", 80)
}

object HasApplyMethod {
  def apply(name: String): String = s"applied $name"
}

object CallsApplyMethod {
  def callsApplySugar: String = HasApplyMethod("value")

  def etaExpandsApply: String => String = HasApplyMethod.apply

  def mapsWithApply(names: List[String]): List[String] = names.map(HasApplyMethod.apply)
}

case class ThrownFailure(reason: String) extends RuntimeException(reason)

object ThrowsFailure {
  def throwsNothing(): Nothing = throw ThrownFailure("thrown")
}
