package software.ralf.storymile.libraryshows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the current user's followed shows for the Library Shows tab. */
interface LibraryShowsPresenter : ComposePresenter<Unit, LibraryShowsPresenter.Model> {
  /** Lets Library choose when to show its shared empty layout. */
  interface Model : BaseModel {
    /** True after a successful load with no followed shows. */
    val isEmpty: Boolean
  }
}
