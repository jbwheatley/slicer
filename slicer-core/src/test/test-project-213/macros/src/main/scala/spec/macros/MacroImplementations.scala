package spec.macros

import scala.reflect.macros.blackbox

object ReflectedTarget {
  val label: String = "reflected"
}

object MacroImplementations {

  def describeImpl(c: blackbox.Context)(value: c.Expr[String]): c.Expr[String] = {
    import c.universe._
    c.Expr[String](q""""described:" + $value""")
  }

  def sizeImpl[A](c: blackbox.Context)(values: c.Expr[List[A]]): c.Expr[Int] = {
    import c.universe._
    c.Expr[Int](q"$values.size")
  }

  def labelOfImpl[A](c: blackbox.Context)(implicit tag: c.WeakTypeTag[A]): c.Expr[String] = {
    import c.universe._
    c.Expr[String](Literal(Constant(weakTypeOf[A].typeSymbol.name.decodedName.toString)))
  }

  def reflectedLabelImpl(c: blackbox.Context): c.Expr[String] = {
    import c.universe._
    val target = c.mirror.staticModule("spec.macros.ReflectedTarget")
    c.Expr[String](q"$target.label")
  }
}
