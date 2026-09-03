package spec.entry

import spec.givens.{CallsTypeClass, TypeClass}
import spec.opaques.{CallsOpaqueType, OpaqueType}
import spec.services.{FirstService, SecondService, ServiceView}

case class InputValue(path: String, key: Long)
case class ResultValue(code: Int, body: String)

class Handler(first: FirstService, second: SecondService) {

  def handlesWithOneParameter(input: InputValue): ResultValue =
    first.reachedFromOneCaller(input.key) match {
      case Some(view) => ResultValue(200, renders(view))
      case None       => ResultValue(404, "missing")
    }

  def handlesWithBothParameters(input: InputValue): ResultValue =
    second.readsBothSources(input.key) match {
      case Some(value) => ResultValue(200, value + first.reachedFromAnotherCaller().size)
      case None        => ResultValue(404, "missing")
    }

  def handlesOpaqueType(input: InputValue): ResultValue =
    ResultValue(200, CallsOpaqueType.formats(OpaqueType(input.key)))

  private def renders(view: ServiceView): String =
    CallsTypeClass.rendersPair((view.key.toInt, view.rendered)) + summon[TypeClass[String]].render(view.value)
}
