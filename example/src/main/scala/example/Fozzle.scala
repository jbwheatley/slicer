package example

final case class Faz(name: String)

object Fozzle {
  //wowowowow so cool
  def fizzle(faz: Faz): String =
    if (faz.name.contains("foo")) Foobar.Foo.hello(faz.name) else Foobar.Bar.hello(faz.name)
}
