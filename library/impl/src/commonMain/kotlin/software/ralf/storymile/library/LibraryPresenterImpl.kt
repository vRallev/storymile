package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel

@Inject
@ContributesBinding(AppScope::class)
class LibraryPresenterImpl : LibraryPresenter {
  @Composable override fun present(input: Unit): Model = Model

  data object Model : BaseModel
}
