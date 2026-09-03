package spec.macros

import scala.language.experimental.macros

trait Labelled[A] {
  def label: String
}

object StringMacros {
  def describe(value: String): String = macro MacroImplementations.describeImpl

  def sizeOf[A](values: List[A]): Int = macro MacroImplementations.sizeImpl[A]

  def labelOf[A]: String = macro MacroImplementations.labelOfImpl[A]

  def reflectedLabel: String = macro MacroImplementations.reflectedLabelImpl
}

object CallsMacros {
  def callsDescribe: String = StringMacros.describe("hello")

  def callsSizeOf: Int = StringMacros.sizeOf(List(1, 2, 3))

  def callsLabelOf: String = StringMacros.labelOf[Labelled[Int]]

  def callsReflectedLabel: String = StringMacros.reflectedLabel
}
