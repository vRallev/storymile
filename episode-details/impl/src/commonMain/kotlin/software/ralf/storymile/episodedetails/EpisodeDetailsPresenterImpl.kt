package software.ralf.storymile.episodedetails

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull

class EpisodeDetailsPresenterImpl(private val details: EpisodeDetails) :
  EpisodeDetailsPresenter<EpisodeDetailsPresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    return Model(
      details = details,
      onPlayNow = {},
      onAddToQueue = {},
      onSave = {},
      onDownload = {},
      onMore = {},
      onBack = { backstack.pop() },
    )
  }

  /** Renderer state retains the selected episode snapshot. */
  data class Model(
    val details: EpisodeDetails,
    val onPlayNow: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onSave: () -> Unit,
    val onDownload: () -> Unit,
    val onMore: () -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
