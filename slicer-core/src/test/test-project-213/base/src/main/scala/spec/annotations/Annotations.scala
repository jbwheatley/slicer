package spec.annotations

import scala.annotation.implicitNotFound
import scala.annotation.tailrec

class CustomAnnotation(val note: String) extends scala.annotation.StaticAnnotation

@implicitNotFound("no Describes instance for ${A}")
trait Describes[A] {
  def describe(a: A): String
}

object Describes {
  implicit val describesInt: Describes[Int] = new Describes[Int] {
    def describe(a: Int): String = s"int:$a"
  }
}

@CustomAnnotation("annotated class")
class AnnotatedClass(val field: String) {
  @deprecated("use readsField instead", "0.1.0")
  def deprecatedMember: String = field

  @inline def inlinedMember: String = field.trim

  def readsField: String = inlinedMember
}

object CallsAnnotated {
  def call(value: String): String = new AnnotatedClass(value).readsField

  def describes(value: Int)(implicit instance: Describes[Int]): String = instance.describe(value)

  @tailrec
  def counts(value: Int, seen: Int = 0): Int = if (value <= 0) seen else counts(value - 1, seen + 1)
}

@SerialVersionUID(1L)
case class SerializableValue(id: Long) extends Serializable
