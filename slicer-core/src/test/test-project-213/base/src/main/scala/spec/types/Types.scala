package spec.types

import scala.language.existentials
import scala.language.reflectiveCalls

trait HasAbstractType {
  type Element
  def first: Element
}

class BindsAbstractType extends HasAbstractType {
  type Element = String
  def first: Element = "bound"
}

object CallsAbstractType {
  def call(source: HasAbstractType): source.Element = source.first
}

trait HigherKinded[F[_]] {
  def wrap[A](a: A): F[A]
}

object HigherKinded {
  val listInstance: HigherKinded[List] = new HigherKinded[List] {
    def wrap[A](a: A): List[A] = List(a)
  }
}

object CallsHigherKinded {
  def call(value: String): List[String] = HigherKinded.listInstance.wrap(value)
}

class OuterWithInnerClass(val label: String) {
  class Inner(val index: Int) {
    def rendered: String = s"$label:$index"
  }

  def makesInner(index: Int): Inner = new Inner(index)
}

object CallsPathDependentType {
  def call(outer: OuterWithInnerClass): String = outer.makesInner(1).rendered
}

object TypeAliases {
  type AliasedFunction = String => Int

  type AliasedTuple = (String, Int)

  def usesAliases(f: AliasedFunction, pair: AliasedTuple): Int = f(pair._1) + pair._2
}

object UsesExistentialType {
  def sizeOf(values: List[_]): Int = values.size

  def countsWildcards(values: List[Map[String, _]]): Int = values.map(_.size).sum
}

object UsesStructuralType {
  type HasClose = { def close(): Unit }

  def closes(resource: HasClose): Unit = resource.close()
}

class Covariant[+A](val value: A) {
  def widened[B >: A]: Covariant[B] = new Covariant[B](value)
}

class Contravariant[-A] {
  def accepts(value: A): String = value.toString
}

object CallsVariance {
  def call: String = new Contravariant[Any].accepts(new Covariant[String]("a").widened[Any].value)
}
