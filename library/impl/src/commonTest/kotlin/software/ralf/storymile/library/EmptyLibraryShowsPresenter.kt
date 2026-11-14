package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.storymile.libraryshows.LibraryShowsPresenter

internal object EmptyLibraryShowsPresenter : LibraryShowsPresenter {
  @Composable override fun present(input: Unit): LibraryShowsPresenter.Model = Model

  data object Model : LibraryShowsPresenter.Model {
    override val isEmpty: Boolean = true
  }
}
