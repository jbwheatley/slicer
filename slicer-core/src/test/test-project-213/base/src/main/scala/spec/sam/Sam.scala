package spec.sam

trait SingleAbstractMethod {
  def transform(value: String): String
}

trait SingleAbstractMethodWithDefault {
  def transform(value: String): String
  def twice(value: String): String = transform(transform(value))
}

object BuildsFromLambda {
  val fromLambda: SingleAbstractMethod = value => value.trim

  val fromAnonymousClass: SingleAbstractMethod = new SingleAbstractMethod {
    def transform(value: String): String = value.reverse
  }

  val withDefaultFromLambda: SingleAbstractMethodWithDefault = value => value.toUpperCase

  def transformsTwice(value: String): String = withDefaultFromLambda.twice(value)
}

object CallsSam {
  def call(value: String): String =
    BuildsFromLambda.fromLambda.transform(value) + BuildsFromLambda.fromAnonymousClass.transform(value)

  def acceptsFunction(f: String => String, value: String): String = f(value)

  def passesMethodReference(value: String): String = acceptsFunction(BuildsFromLambda.fromLambda.transform, value)
}
