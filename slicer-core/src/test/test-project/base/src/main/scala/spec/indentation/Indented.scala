package spec.indentation

trait IndentedTrait:
  def describe: String
  def shout: String = describe.toUpperCase

class IndentedClass(val label: String) extends IndentedTrait:
  def describe: String = label

  def repeated(times: Int): String =
    if times <= 0 then ""
    else label * times
end IndentedClass

object IndentedObject:
  val default: IndentedClass = IndentedClass("default")

  def choose(flag: Boolean): IndentedTrait =
    if flag then default
    else IndentedObject.fallback

  def fallback: IndentedTrait =
    new IndentedTrait:
      def describe: String = "fallback"

  def joined(labels: List[String]): String =
    val kept =
      for
        label <- labels
        if label.nonEmpty
      yield label.trim
    kept.mkString(", ")

  def counted(labels: List[String]): Int =
    var total = 0
    for label <- labels do total += label.length
    total

  def classify(value: Int): String = value match
    case 0          => "zero"
    case n if n < 0 => "negative"
    case _          => "positive"
end IndentedObject

trait IndentedMembers:
  def firstMember: String
  def secondMember: String

class HoldsIndentedMembers(source: IndentedMembers):
  def name: String = source.toString

enum IndentedEnum:
  case First
  case Second(reason: String)

  def reasonOrEmpty: String = this match
    case First          => ""
    case Second(reason) => reason

object IndentedGivens:
  trait Renderer[A]:
    def render(value: A): String

  given Renderer[Int]:
    def render(value: Int): String = value.toString

  given stringRenderer: Renderer[String] with
    def render(value: String): String = value

  def renderAll[A](values: List[A])(using renderer: Renderer[A]): String =
    values.map(renderer.render).mkString("|")

extension (self: IndentedClass)
  def loud: String =
    self.describe.toUpperCase + "!"

  def quiet: String = self.describe.toLowerCase
