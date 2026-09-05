package spec.refinements

import reflect.Selectable.reflectiveSelectable

trait HasStore {
  def stored: String
}

object ReadsRefinedMember {
  type RefinedWithMember = HasStore { def extra: String }

  def readsRefined(value: RefinedWithMember): String = value.stored + value.extra
}
