package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.library.LibraryPresenter

@Inject
class AppRootPresenter(private val libraryPresenter: LibraryPresenter) :
  ComposePresenter<Unit, BaseModel> {
  @Composable override fun present(input: Unit): BaseModel = libraryPresenter.present(Unit)
}
