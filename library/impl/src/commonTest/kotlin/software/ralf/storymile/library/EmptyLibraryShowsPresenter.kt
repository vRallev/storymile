package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.libraryshows.LibraryShowsPresenter

internal object EmptyLibraryShowsPresenter : LibraryShowsPresenter {
  @Composable override fun present(input: Unit): LibraryShowsPresenter.Model = Model

  data object Model : LibraryShowsPresenter.Model {
    override val isEmpty: Boolean = true
    override val list: BaseModel
      get() = this

    override val selectedShow: BaseModel? = null
    override val onOpenFirstShow: (() -> Unit)? = null

    override val onCloseEpisode: (() -> Unit)? = null
    override val onCloseShow: () -> Unit = {}
  }
}
