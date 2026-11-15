package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.libraryqueue.LibraryQueuePresenter

internal object EmptyLibraryQueuePresenter : LibraryQueuePresenter {
  @Composable override fun present(input: Unit): LibraryQueuePresenter.Model = Model

  data object Model : LibraryQueuePresenter.Model {
    override val isEmpty: Boolean = true
    override val list: BaseModel
      get() = this

    override val openedEpisode: BaseModel? = null
    override val onCloseEpisode: () -> Unit = {}
  }
}
