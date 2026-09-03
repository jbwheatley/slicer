package spec.types

type UnionType = Long | String

type MatchType[A] = A match {
  case List[t]   => t
  case Option[t] => t
}

type TypeLambda[F[_]] = [A] =>> F[List[A]]

trait HasIdentifier {
  def identifier: UnionType
}

trait HasLabel {
  def label: String
}

case class ImplementsBothTraits(identifier: UnionType, label: String) extends HasIdentifier with HasLabel

object MatchesTypes {
  def matchesUnionType(value: UnionType): String = value match {
    case long: Long     => long.toString
    case string: String => string
  }

  def takesIntersectionType(value: HasIdentifier & HasLabel): String =
    s"${value.label}#${matchesUnionType(value.identifier)}"

  def returnsMatchType(values: List[String]): MatchType[List[String]] = values.head
}

trait HasAbstractTypeMember {
  type Key
  def get(key: Key): String
}

object BindsAbstractTypeMember extends HasAbstractTypeMember {
  type Key = String
  def get(key: Key): String = key.reverse
}

object CallsRefinedType {
  type RefinedType = HasAbstractTypeMember { type Key = String }

  def readsRefined(value: RefinedType, key: String): String = value.get(key)
}

trait HasPathDependentType {
  type Out
  def zero: Out
}

object BindsPathDependentType extends HasPathDependentType {
  type Out = Int
  def zero: Out = 0
}

object CallsPathDependentType {
  def readsPathDependent(value: HasPathDependentType): value.Out = value.zero
}

trait HigherKinded[F[_]] {
  def wrap[A](a: A): F[A]
}

object CallsHigherKinded {
  given higherKindedForOption: HigherKinded[Option] = new HigherKinded[Option] {
    def wrap[A](a: A): Option[A] = Some(a)
  }

  def wraps[F[_], A](a: A)(using instance: HigherKinded[F]): F[A] = instance.wrap(a)

  def wrapsInOption: Option[Int] = wraps[Option, Int](1)
}

trait HigherKindedWithTuple[F[_]] {
  def zip[A, B](left: F[A], right: F[B]): F[(A, B)]
}

object HigherKindedWithTuple {
  given tupleForOption: HigherKindedWithTuple[Option] = new HigherKindedWithTuple[Option] {
    def zip[A, B](left: Option[A], right: Option[B]): Option[(A, B)] =
      left.flatMap(a => right.map(b => (a, b)))
  }

  inline given tupleForList: HigherKindedWithTuple[List] = new HigherKindedWithTuple[List] {
    def zip[A, B](left: List[A], right: List[B]): List[(A, B)] = left.zip(right)
  }
}

object CallsHigherKindedWithTuple {
  def zipsOptions: Option[(Int, String)] = summon[HigherKindedWithTuple[Option]].zip(Some(1), Some("a"))

  def zipsLists: List[(Int, String)] = summon[HigherKindedWithTuple[List]].zip(List(1), List("a"))
}

object CallsStructuralType {
  import reflect.Selectable.reflectiveSelectable

  type StructuralType = { def size: Int }

  def readsStructurally(value: StructuralType): Int = value.size
}
