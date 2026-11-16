package software.ralf.storymile.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tracks the current Undo action for a Library list. */
class LibraryRemovalUndo {
  /** The latest accepted removal, until it is undone, dismissed, or replaced. */
  val pending: StateFlow<Removal?>
    field = MutableStateFlow<Removal?>(null)

  /** Replaces the current offer with [undo], which restores the removed episode. */
  fun offer(undo: () -> Unit) {
    pending.value =
      object : Removal {
        override fun undo() {
          if (pending.compareAndSet(expect = this, update = null)) {
            undo.invoke()
          }
        }

        override fun dismiss() {
          pending.compareAndSet(expect = this, update = null)
        }
      }
  }

  /** An Undo offer for one removal. */
  interface Removal {
    /** Restores the episode if this offer is current. */
    fun undo()

    /** Clears this offer if it is current. */
    fun dismiss()
  }
}
