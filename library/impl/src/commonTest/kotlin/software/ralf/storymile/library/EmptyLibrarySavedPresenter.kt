package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.librarysaved.LibrarySavedPresenter

internal object EmptyLibrarySavedPresenter : LibrarySavedPresenter {
  @Composable override fun present(input: Unit): LibrarySavedPresenter.Model = Model

  data object Model : LibrarySavedPresenter.Model {
    override val isEmpty: Boolean = true
    override val list: BaseModel
      get() = this

    override val openedEpisode: BaseModel? = null
    override val onCloseEpisode: () -> Unit = {}
    override val removal: LibraryRemovalUndo.Removal? = null
  }
}
