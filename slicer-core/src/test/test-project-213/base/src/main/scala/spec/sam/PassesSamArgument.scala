package spec.sam

object TakesSam {
  def runs(sam: SingleAbstractMethod, value: String): String = sam.transform(value)

  def runsTwice(sam: SingleAbstractMethodWithDefault, value: String): String = sam.twice(value)
}

object PassesSamArgument {
  def passesLambda(value: String): String = TakesSam.runs(input => input.trim, value)

  def passesLambdaWithDefault(value: String): String = TakesSam.runsTwice(input => input.trim, value)
}
