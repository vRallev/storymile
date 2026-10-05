package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel

@Inject
@ContributesBinding(AppScope::class)
class TabsPresenterImpl : TabsPresenter {
  @Composable override fun present(input: Unit): Model = Model

  data object Model : BaseModel
}
