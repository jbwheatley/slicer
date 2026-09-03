package spec.javacalls

import spec.javadefs.Registry

object CallsJava {

  def describeRegistry(name: String): String = new Registry(name).describe()

  def widthOfRegistry(name: String): Int = new Registry(name).width()
}
