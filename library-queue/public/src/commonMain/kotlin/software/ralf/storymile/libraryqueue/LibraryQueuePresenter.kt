package software.ralf.storymile.libraryqueue

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.library.LibraryRemovalUndo

/** Presents the current user's queue for the Library Queue tab. */
interface LibraryQueuePresenter : ComposePresenter<Unit, LibraryQueuePresenter.Model> {
  /** Lets Library show the queue, its empty state, or an opened episode. */
  interface Model : BaseModel {
    /** True after the queue loads with no episodes. */
    val isEmpty: Boolean

    /** The queue list with its loading state and episode preview. */
    val list: BaseModel

    /** The details of an episode the user selected, or null at the queue root. */
    val openedEpisode: BaseModel?

    /** Closes the opened episode and returns to the queue. */
    val onCloseEpisode: () -> Unit

    /** The latest removal that can still be undone, or null when there is none. */
    val removal: LibraryRemovalUndo.Removal?
  }
}
