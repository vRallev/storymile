package software.ralf.storymile.libraryshows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the current user's followed shows for the Library Shows tab. */
interface LibraryShowsPresenter : ComposePresenter<Unit, LibraryShowsPresenter.Model> {
  /** Lets Library choose when to show its shared empty layout. */
  interface Model : BaseModel {
    /** True after an empty load when no show is open. */
    val isEmpty: Boolean

    /** Navigation content for the followed list and its selected show. */
    val content: BaseModel

    /** Navigates back from the active show or episode. Null when only the list is open. */
    val onBack: (() -> Unit)?

    /** Returns to the list from any show destination. */
    val onCloseShow: () -> Unit
  }
}
