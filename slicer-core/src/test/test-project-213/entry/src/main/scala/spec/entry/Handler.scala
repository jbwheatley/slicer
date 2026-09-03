package spec.entry

import spec.implicits.{CallsTypeClass, TypeClass}
import spec.implicitclasses.StringSyntax._
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

  def handlesWithImplicitClass(input: InputValue): ResultValue = ResultValue(200, input.path.shouted)

  private def renders(view: ServiceView): String =
    CallsTypeClass.rendersPair((view.key.toInt, view.rendered)) + implicitly[TypeClass[String]].render(view.value)
}
