package spec.entry

import spec.implementations.{DelegatingImplementation, DirectImplementation, SourceImplementation}
import spec.services.{FirstService, SecondService}

object Main {
  def main(args: Array[String]): Unit = {
    val direct = new DirectImplementation(new SourceImplementation)
    val delegating = new DelegatingImplementation(direct)
    val handler = new Handler(new FirstService(direct), new SecondService(direct, delegating))
    println(handler.handlesWithOneParameter(InputValue("/values/1", 1L)).body)
  }
}
