package spec.inheritance

trait TraitWithParameter(val parameter: String) {
  def fromParameter: String = s"trait:$parameter"
}

class ExtendsTraitWithParameter(argument: String) extends TraitWithParameter(argument) {
  override def fromParameter: String = super.fromParameter.toUpperCase
}

abstract class AbstractClassWithParameter(val count: Int) {
  def abstractMember: String
  def usesAbstractMember: String = s"$abstractMember x $count"
}

class ExtendsAbstractClass extends AbstractClassWithParameter(2) {
  def abstractMember: String = "member"

  override def usesAbstractMember: String = super.usesAbstractMember + "!"
}

trait RequiresSelfType {
  self: HasStoreName =>
  def usesSelfType: String = s"self:$storeName"
}

trait HasStoreName {
  def storeName: String
  lazy val lazyBanner: String = s"store: $storeName"
}

object MixesInSelfType extends HasStoreName with RequiresSelfType {
  def storeName: String = "store"
}

trait LinearizedBase {
  def linearized: String = "base"
}

trait LinearizedLeft extends LinearizedBase {
  override def linearized: String = super.linearized.toUpperCase
}

trait LinearizedRight extends LinearizedBase {
  override def linearized: String = super.linearized.toLowerCase
}

class MixesBothSides extends LinearizedLeft with LinearizedRight {
  override def linearized: String = super[LinearizedLeft].linearized + "/" + super.linearized
}

object CallsLinearized {
  def call: String = new MixesBothSides().linearized
}

trait Stackable {
  def process(in: String): String
}

trait StackableTrimming extends Stackable {
  abstract override def process(in: String): String = super.process(in).trim
}

trait StackableUpper extends Stackable {
  abstract override def process(in: String): String = super.process(in).toUpperCase
}

class StackableBase extends Stackable {
  def process(in: String): String = in
}

object CallsStackable {
  def stacked(in: String): String = new StackableBase with StackableTrimming with StackableUpper().process(in)

  def anonymousImplementation: Stackable = new Stackable {
    def process(in: String): String = in.reverse
  }
}

object NamesSelfTyped {
  def names(value: RequiresSelfType): String = value.toString
}
