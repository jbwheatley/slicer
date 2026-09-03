package spec.extensions

import spec.derivation.DerivableTypeClass

extension [A](values: List[A]) {
  def genericExtensionMethod: Option[A] = values.drop(1).headOption

  def extensionMethodWithUsing(tag: String)(using instance: DerivableTypeClass[A]): List[String] =
    values.map(value => s"$tag:${instance.label(value)}")
}

object CallsGenericExtensions {
  def callsGenericExtensionMethod(values: List[Int]): Option[Int] = values.genericExtensionMethod

  def callsExtensionMethodWithUsing(values: List[Int]): List[String] = values.extensionMethodWithUsing("n")
}
