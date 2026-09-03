package spec.macros

trait Labelled[A] {
  def label: String
}

object StringMacros {
  inline def describe(inline value: String): String = ${ MacroImplementations.describeImpl('value) }

  inline def sizeOf[A](inline values: List[A]): Int = ${ MacroImplementations.sizeImpl('values) }

  inline def labelOf[A]: String = ${ MacroImplementations.labelOfImpl[A] }

  inline def reflectedLabel: String = ${ MacroImplementations.reflectedLabelImpl }
}

object CallsMacros {
  def callsDescribe: String = StringMacros.describe("hello")

  def callsSizeOf: Int = StringMacros.sizeOf(List(1, 2, 3))

  def callsLabelOf: String = StringMacros.labelOf[Labelled[Int]]

  def callsReflectedLabel: String = StringMacros.reflectedLabel
}
