package spec.configured

trait Mapper[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

object Mapper {
  val orFailure: Mapper[Either[String, *]] = new Mapper[Either[String, *]] {
    override def map[A, B](fa: Either[String, A])(f: A => B): Either[String, B] = fa.map(f)
  }
}

case class Labelled[A](value: A, label: String)

object Labelled {
  def here[A](value: A)(implicit name: sourcecode.Name): Labelled[A] = Labelled(value, name.value)
}

object Configured {
  def labelledMapper: Labelled[Mapper[Either[String, *]]] = Labelled.here(Mapper.orFailure)
}
