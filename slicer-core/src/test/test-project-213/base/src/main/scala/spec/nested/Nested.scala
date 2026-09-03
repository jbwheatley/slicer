package spec.nested

object OuterObject {
  object InnerObject {
    def innerDefinition: String = "inner"

    object DeeplyNested {
      def deepDefinition: String = innerDefinition + ":deep"
    }
  }

  class InnerClass(val name: String) {
    def rendered: String = s"$name:${InnerObject.innerDefinition}"
  }

  def callsInner: String = InnerObject.DeeplyNested.deepDefinition
}

object CallsNested {
  def call: String = OuterObject.callsInner + new OuterObject.InnerClass("x").rendered
}

class HasLocalDefinitions(seed: Int) {
  def computes(value: Int): Int = {
    def localHelper(inner: Int): Int = inner + seed

    val localValue = localHelper(value)
    localValue * 2
  }
}

object CompanionPair {
  def fromCompanion(value: String): CompanionPair = new CompanionPair(value)
}

class CompanionPair(val value: String) {
  def usesCompanion: CompanionPair = CompanionPair.fromCompanion(value + "!")
}
