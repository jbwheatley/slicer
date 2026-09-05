package spec.javacalls

import spec.javadefs.{Outer, Status}

object CallsNestedJava {

  def labelOfStatus(status: Status): String = status.label()

  def describesInner(label: String): String = new Outer().makeInner(label).describe()
}
