package spec.values

object HoldsValues {
  val eagerValue: String = "eager"

  lazy val lazyValue: String = eagerValue + ":lazy"

  var mutableValue: Int = 0

  final val constantValue = 7

  def readsAll: String = s"$eagerValue$lazyValue$mutableValue$constantValue"

  def writesMutableValue(next: Int): Unit = mutableValue = next
}

class HasFields(initial: String) {
  val fieldFromParameter: String = initial

  private val privateField: String = initial.reverse

  var mutableField: String = initial

  def readsPrivateField: String = privateField

  def readsFieldFromParameter: String = fieldFromParameter
}

object CallsFields {
  def call(initial: String): String = {
    val holder = new HasFields(initial)
    holder.mutableField = initial + "!"
    holder.readsPrivateField + holder.readsFieldFromParameter + holder.mutableField
  }
}

object HasBlockInitialisedValue {
  val computedValue: String = {
    val parts = List("a", "b")
    parts.mkString
  }
}
