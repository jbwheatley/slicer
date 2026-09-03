package spec.bindings

trait DeclaresMutableMember {
  var declaredVar: Int
  def readsDeclaredVar: Int = declaredVar
}

class FixesMutableMember extends DeclaresMutableMember {
  var declaredVar: Int = 1
}

enum Colour {
  case Red, Green
  case Blue
}

class TakesTwoParameters(val label: String, val count: Int) {
  def this(label: String) = this(label, 1)

  def describe: String = s"$label:$count"
}

object BoundNames {
  val firstOfTwo, secondOfTwo = 1

  var firstMutable, secondMutable = 2

  val (leftOfPair, rightOfPair) = (3, 4)

  val Some(unwrapped) = Option("bound"): @unchecked
}

object ReadsBoundNames {
  def readsFirstOfTwo: Int = BoundNames.firstOfTwo

  def readsLeftOfPair: Int = BoundNames.leftOfPair

  def readsUnwrapped: String = BoundNames.unwrapped

  def readsRepeatedCase: Colour = Colour.Green

  def readsSecondaryConstructor: String = new TakesTwoParameters("only").describe
}
