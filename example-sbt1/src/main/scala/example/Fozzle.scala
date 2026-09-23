package example

import cats.syntax.show.*

final case class Faz(name: String)

object Fozzle {
  def fizzle(faz: Faz): String =
    if (faz.name.contains("foo")) Foobar.Foo.hello(faz.name.show) else Foobar.Bar.hello(faz.name)
}
