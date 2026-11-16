package software.ralf.storymile.library

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the user's library and its discovery navigation stack for the app's content slot. */
interface LibraryPresenter<ModelT : LibraryPresenter.Model> : ComposePresenter<Unit, ModelT> {
  /** Library content with its tab-selection action. */
  interface Model : BaseModel {
    /** Returns to the selected Library page and closes open details. Safe to call at the root. */
    val onLibrarySelected: () -> Unit
  }
}
