package spec.givens

trait TypeClass[A] {
  def render(a: A): String
}

object TypeClass {
  given typeClassForInt: TypeClass[Int] = value => value.toString

  given typeClassForString: TypeClass[String] = identity

  given typeClassForList[A](using inner: TypeClass[A]): TypeClass[List[A]] =
    values => values.map(inner.render).mkString("[", ", ", "]")

  given typeClassForPair[A, B](using left: TypeClass[A], right: TypeClass[B]): TypeClass[(A, B)] =
    pair => s"(${left.render(pair._1)}, ${right.render(pair._2)})"

  def renderWithContextBound[A: TypeClass](a: A): String = summon[TypeClass[A]].render(a)
}

object CallsTypeClass {
  def rendersList(values: List[Int]): String = TypeClass.renderWithContextBound(values)

  def rendersPair(pair: (Int, String)): String = TypeClass.renderWithContextBound(pair)
}

trait PriorityTypeClass[A] {
  def encode(a: A): String
}

trait LowPriorityInstances {
  given fallbackInstance[A]: PriorityTypeClass[A] = a => a.toString
}

object PriorityTypeClass extends LowPriorityInstances {
  given specificInstance: PriorityTypeClass[Int] = value => s"i$value"
}

object CallsPriorityTypeClass {
  def encodeWithUsing[A](a: A)(using instance: PriorityTypeClass[A]): String = instance.encode(a)

  def picksSpecificInstance: String = encodeWithUsing(1)

  def picksFallbackInstance: String = encodeWithUsing(1.5)
}

case class HasGivensInCompanion(code: String)

object HasGivensInCompanion {
  given TypeClass[HasGivensInCompanion] = value => s"companion:${value.code}"

  given Ordering[HasGivensInCompanion] = Ordering.by(_.code)
}

object CallsGivensInCompanionScope {
  def rendersFromCompanion(value: HasGivensInCompanion): String =
    summon[TypeClass[HasGivensInCompanion]].render(value)

  def sortsWithCompanionOrdering(values: List[HasGivensInCompanion]): List[HasGivensInCompanion] = values.sorted
}

class TakesUsingClauseInConstructor(label: String)(using renderer: TypeClass[String]) {
  def rendersLabel: String = renderer.render(label)

  def echoesLabel: String = label
}

class NeverSummonsAnonymousUsingClause(label: String)(using TypeClass[String]) {
  def echoesLabel: String = label
}

class SummonsAnonymousUsingClause(label: String)(using TypeClass[String]) {
  def rendersLabel: String = summon[TypeClass[String]].render(label)
}

class TakesOnlyAUsingClause(using renderer: TypeClass[String]) {
  def rendersLiteral: String = renderer.render("only")

  def echoesLiteral: String = "only"
}

class TakesUsingClauseBeforeTerms(label: String)(using renderer: TypeClass[String])(suffix: String) {
  def rendersLabel: String = renderer.render(label)

  def echoesLabel: String = label + suffix
}

class TakesTwoUsingClauses(label: String)(using renderer: TypeClass[String])(using counter: TypeClass[Int]) {
  def rendersLabel: String = renderer.render(label)

  def rendersCount: String = counter.render(1)

  def echoesLabel: String = label
}

trait ContextParameter {
  def value: String
}

object CallsContextFunction {
  type ContextFunction[A] = ContextParameter ?=> A

  def readsContext: ContextFunction[String] = summon[ContextParameter].value

  def runsWithContext[A](context: ContextParameter)(body: ContextFunction[A]): A = body(using context)

  def callsWithAnonymousImplementation: String = runsWithContext(new ContextParameter { def value = "ada" })(readsContext)
}

given givenConversion: Conversion[String, Long] = _.length

object CallsGivenConversion {
  def convertsImplicitly(value: String): Long = value
}

object OldStyleImplicits {
  implicit val implicitValue: String = "implicit"

  implicit def implicitConversion(value: Int): String = value.toString

  def takesImplicitParameter(implicit value: String): String = s"implicit:$value"

  def callsWithImplicitParameter: String = takesImplicitParameter

  def callsImplicitConversion: String = takesImplicitParameter(1)
}
