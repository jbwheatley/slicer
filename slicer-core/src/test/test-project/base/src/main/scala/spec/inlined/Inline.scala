package spec.inlined

import scala.compiletime.summonInline

trait InlineSummonedTypeClass[A] {
  def encode(a: A): String
}

object InlineSummonedTypeClass {
  given instanceForString: InlineSummonedTypeClass[String] = value => s"str:$value"

  inline def encodeInline[A](a: A): String = summonInline[InlineSummonedTypeClass[A]].encode(a)

  transparent inline def transparentInline(value: Int): Any = value
}

inline def inlineDefinition(inline value: String): String = value + value

object CallsInlineDefinitions {
  def callsInlineDefinition: String = inlineDefinition("ab")

  def callsInlineSummon: String = InlineSummonedTypeClass.encodeInline("x")
}
