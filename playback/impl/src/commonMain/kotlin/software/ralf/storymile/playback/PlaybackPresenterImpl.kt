package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel

@Inject
@ContributesBinding(AppScope::class)
class PlaybackPresenterImpl : PlaybackPresenter {
  @Composable override fun present(input: Unit): Model = Model

  data object Model : BaseModel
}
