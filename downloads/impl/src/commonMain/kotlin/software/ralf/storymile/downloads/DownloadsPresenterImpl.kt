package software.ralf.storymile.downloads

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.BaseModel

@Inject
@ContributesBinding(AppScope::class, binding = binding<DownloadsPresenter<*>>())
class DownloadsPresenterImpl : DownloadsPresenter<DownloadsPresenterImpl.Model> {
  @Composable override fun present(input: Unit): Model = Model

  data object Model : BaseModel
}
