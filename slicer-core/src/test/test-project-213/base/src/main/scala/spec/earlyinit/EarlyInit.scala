package spec.earlyinit

trait ReadsInitializedField {
  def initializedField: String
  val derivedFromField: String = s"derived:$initializedField"
}

class InitializesBeforeMixin
    extends {
      val initializedField: String = "early"
    }
    with ReadsInitializedField

object CallsEarlyInitializer {
  def call: String = new InitializesBeforeMixin().derivedFromField
}
