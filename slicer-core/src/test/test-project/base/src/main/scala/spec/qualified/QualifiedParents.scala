package spec.qualified

class ExtendsQualifiedParent extends spec.inheritance.HasStoreName {
  def storeName: String = "qualified"
}

trait FirstMixin {
  def first: String = "first"
}

trait SecondMixin {
  def second: String = "second"
}

trait RequiresIntersectionSelfType {
  self: FirstMixin & SecondMixin =>
  def usesBothMixins: String = first + second
}

object MixesIntersectionSelfType extends FirstMixin with SecondMixin with RequiresIntersectionSelfType {
  def mixed: String = usesBothMixins
}

object BuildsQualifiedInstance {
  def builds: spec.inheritance.HasStoreName = new spec.qualified.ExtendsQualifiedParent
}
