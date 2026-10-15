package software.ralf.storymile.home

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the welcome screen and its discovery navigation stack. */
interface HomePresenter<ModelT : HomePresenter.Model> : ComposePresenter<Unit, ModelT> {
  /** Home content with its tab-selection action. */
  interface Model : BaseModel {
    /** Returns to the welcome screen without recreating its entry. Safe to call at the root. */
    val onHomeSelected: () -> Unit
  }
}
