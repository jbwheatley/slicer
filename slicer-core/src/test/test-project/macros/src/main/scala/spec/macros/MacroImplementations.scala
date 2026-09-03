package spec.macros

import scala.quoted.*

object ReflectedTarget {
  val label: String = "reflected"
}

object MacroImplementations {

  def describeImpl(value: Expr[String])(using Quotes): Expr[String] =
    '{ "described:" + $value }

  def sizeImpl[A: Type](values: Expr[List[A]])(using Quotes): Expr[Int] =
    '{ $values.size }

  def labelOfImpl[A: Type](using quotes: Quotes): Expr[String] = {
    import quotes.reflect.*
    Expr(TypeRepr.of[A].typeSymbol.name)
  }

  def reflectedLabelImpl(using quotes: Quotes): Expr[String] = {
    import quotes.reflect.*
    val target = Symbol.requiredModule("spec.macros.ReflectedTarget")
    Ref(target).select(target.declaredField("label")).asExprOf[String]
  }
}
