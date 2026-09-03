package spec.services

import spec.implementations.AbstractWithTwoMembers
import spec.members.OverloadedMembers

class FirstService(source: AbstractWithTwoMembers) {
  def reachedFromOneCaller(key: Long): Option[ServiceView] =
    source.calledMember(key).map(value => ServiceView(key, value, OverloadedMembers.overloaded(value)))

  def reachedFromAnotherCaller(): List[ServiceView] =
    source.uncalledMember().map(value => ServiceView(0L, value, OverloadedMembers.overloaded(value)))
}

object FirstService {
  def wrapping(source: AbstractWithTwoMembers): FirstService = new FirstService(source)
}

case class ServiceView(key: Long, value: String, rendered: String)

class SecondService(source: AbstractWithTwoMembers, other: AbstractWithTwoMembers) {
  def readsBothSources(key: Long): Option[String] =
    source.calledMember(key).map(value => joins(other.uncalledMember(), value))

  private def joins(values: List[String], value: String): String = (value :: values).mkString(",")
}
