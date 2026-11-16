package software.ralf.storymile.librarysaved

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the current user's saved episodes in Library. */
interface LibrarySavedPresenter : ComposePresenter<Unit, LibrarySavedPresenter.Model> {
  /** Lets Library show the saved list, its empty state, or an opened episode. */
  interface Model : BaseModel {
    /** True after the saved list loads with no episodes. */
    val isEmpty: Boolean

    /** The saved list with its loading state and episode preview. */
    val list: BaseModel

    /** Details of the selected episode, or null at the saved list root. */
    val openedEpisode: BaseModel?

    /** Returns to the saved list. */
    val onCloseEpisode: () -> Unit
  }
}
