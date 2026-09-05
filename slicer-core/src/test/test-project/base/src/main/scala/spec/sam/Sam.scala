package spec.sam

trait Transformer {
  def transform(value: String): String
}

trait TransformerWithDefault {
  def transform(value: String): String
  def twice(value: String): String = transform(transform(value))
}

object TakesTransformer {
  def runs(transformer: Transformer, value: String): String = transformer.transform(value)

  def runsTwice(transformer: TransformerWithDefault, value: String): String = transformer.twice(value)
}

object PassesLambdaAsArgument {
  def passesLambda(value: String): String = TakesTransformer.runs(input => input.trim, value)

  def passesLambdaWithDefault(value: String): String = TakesTransformer.runsTwice(input => input.trim, value)
}
