package spec.bindings

class BindsInClassBody(seed: Int) {
  val (firstField, secondField) = (seed, seed + 1)

  val thirdField, fourthField = seed * 2

  def readsFirstField: Int = firstField

  def readsThirdField: Int = thirdField
}

object CallsBindsInClassBody {
  def calls(seed: Int): Int = new BindsInClassBody(seed).readsFirstField
}
