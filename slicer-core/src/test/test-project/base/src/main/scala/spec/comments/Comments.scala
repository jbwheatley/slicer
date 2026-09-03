package spec.comments

object BlockCommented {
  /** Kept definition documented across lines
      with a continuation that has no star
   */
  def keptWithUnalignedScaladoc: String = "kept"

  /** Dropped definition documented across lines
      with a continuation that has no star
   */
  def droppedWithUnalignedScaladoc: String = "dropped"

  /*
   * Dropped definition with an aligned block comment
   */
  def droppedWithAlignedBlock: String = "dropped"
}

object CallsBlockCommented {
  def calls: String = BlockCommented.keptWithUnalignedScaladoc
}
