package software.ralf.storymile.libraryshows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the current user's followed shows for the Library Shows tab. */
interface LibraryShowsPresenter : ComposePresenter<Unit, LibraryShowsPresenter.Model> {
  /** Lets Library choose when to show its shared empty layout. */
  interface Model : BaseModel {
    /** True after a successful load with no followed shows. */
    val isEmpty: Boolean

    /** The list of followed shows, including its loading state. */
    val list: BaseModel

    /** The current show's navigation model, or null when only the list is open. */
    val selectedShow: BaseModel?

    /** Opens the first listed show if no show is open. Null until there is a followed show. */
    val onOpenFirstShow: (() -> Unit)?

    /** Closes the episode details. Null when no episode is open. */
    val onCloseEpisode: (() -> Unit)?

    /** Returns to the list from any show destination. */
    val onCloseShow: () -> Unit
  }
}
