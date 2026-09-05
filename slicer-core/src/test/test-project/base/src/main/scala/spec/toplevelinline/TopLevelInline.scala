package spec.toplevelinline

import scala.compiletime.summonInline

trait Encoder[A] {
  def encode(a: A): String
}

given encoderForInt: Encoder[Int] = value => s"n$value"

inline def encodesAtTopLevel(value: Int): String = summonInline[Encoder[Int]].encode(value)

object CallsTopLevelInline {
  def calls(value: Int): String = encodesAtTopLevel(value)
}
