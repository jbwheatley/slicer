package example

sealed trait Foobar {
  def hello(name: String): String
}

object Foobar {
  case object Foo extends Foobar {
    override def hello(name: String): String = s"fooooo $name"
  }

  object Bar extends Foobar {
    override def hello(name: String): String = s"baaaaaar $name"
  }
}
