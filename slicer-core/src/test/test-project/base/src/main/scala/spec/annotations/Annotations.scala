package spec.annotations

import scala.annotation.StaticAnnotation

class annotationWithParameter(val path: String) extends StaticAnnotation

object AnnotationArgument {
  val constantUsedInAnnotation: String = "/constant"
}

@annotationWithParameter(AnnotationArgument.constantUsedInAnnotation)
class AnnotatedClass {
  def memberOfAnnotatedClass: String = "member"
}

object AnnotatedMembers {
  @annotationWithParameter("/annotated")
  def annotatedDefinition(values: List[String], key: String): Option[String] = values.find(_ == key)

  def definitionWithAnnotatedParameter(@deprecatedName("old") value: Int): Int = value * 2
}

case class HasDeprecatedField(@deprecated("use identifier", "1.0") legacyIdentifier: Long, identifier: Long)

object ReadsDeprecatedField {
  def readsIdentifier(value: HasDeprecatedField): Long = value.identifier
}
