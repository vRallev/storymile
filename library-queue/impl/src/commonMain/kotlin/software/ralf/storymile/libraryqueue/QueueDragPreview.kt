package software.ralf.storymile.libraryqueue

internal class QueueDragPreview<T>(
  private val episodes: List<T>,
  private val source: Int,
  private val target: Int,
) : AbstractList<T>() {
  override val size: Int
    get() = episodes.size

  override fun get(index: Int): T =
    episodes[
      when {
        index == target -> source
        index in source until target -> index + 1
        index in (target + 1)..source -> index - 1
        else -> index
      }]
}
