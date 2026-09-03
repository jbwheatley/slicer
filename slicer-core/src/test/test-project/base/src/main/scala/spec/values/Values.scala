package spec.values

class HasMutableField {
  private var mutableField: Int = 0

  def incrementsMutableField(): Int = {
    mutableField += 1
    mutableField
  }

  def readsMutableField: Int = mutableField
}

object CallsMutableField {
  def callsTwice(): Int = {
    val counter = new HasMutableField
    counter.incrementsMutableField()
    counter.incrementsMutableField()
  }
}

final class ValueClass(val raw: Long) extends AnyVal {
  def next: ValueClass = new ValueClass(raw + 1)
}

object CallsValueClass {
  def calls(value: ValueClass): ValueClass = value.next
}

implicit class ImplicitValueClass(val value: String) extends AnyVal {
  def implicitExtensionMethod: String = value.padTo(10, ' ')
}

object CallsImplicitValueClass {
  def calls(value: String): String = value.implicitExtensionMethod
}

object HasLazyValue {
  lazy val lazyValue: String = "lazy"

  val eagerValue: String = "eager"

  def readsBoth: String = lazyValue + eagerValue
}
