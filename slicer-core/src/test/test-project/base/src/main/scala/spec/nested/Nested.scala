package spec.nested

class HasInnerClass(val outerField: String) {
  class InnerClass(val innerField: String) {
    def usesOuterField: String = s"$outerField/$innerField"
  }

  def makesInnerClass(innerField: String): InnerClass = new InnerClass(innerField)
}

object CallsInnerClass {
  def calls(): String = new HasInnerClass("outer").makesInnerClass("inner").usesOuterField
}

object HasNestedObject {
  object NestedObject {
    val nestedValue: String = "deep"

    def readsNestedValue: String = nestedValue
  }

  export NestedObject.{readsNestedValue as renamedExport}

  def callsRenamedExport: String = renamedExport
}

object ExportsMember {
  private val exported = new HasInnerClass("exported")
  export exported.outerField

  def callsExportedMember: String = outerField
}

object ExportsEveryMember {
  export HasNestedObject.NestedObject.*
}

object CallsWildcardExport {
  def callsForwardedMember: String = ExportsEveryMember.readsNestedValue
}
