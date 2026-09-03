package spec.imports

import spec.imports.ImportedMembers.{unusedConstant, usedConstant, usedMember}
import spec.imports.wholly.*

object ReadsImportedMembers {
  def readsUsedConstant: Int = usedConstant

  def callsUsedMember(value: Int): Int = usedMember(value)

  def readsUnusedConstant: Int = unusedConstant

  def readsUnusedPackage: Int = UnusedInWholePackage.unusedInPackage
}
