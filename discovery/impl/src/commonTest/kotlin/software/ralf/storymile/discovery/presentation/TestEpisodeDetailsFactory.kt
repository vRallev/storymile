package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter

class TestEpisodeDetailsFactory : EpisodeDetailsPresenter.Factory {
  var details: EpisodeDetails? = null
  val presenter =
    object : EpisodeDetailsPresenter<BaseModel> {
      @Composable override fun present(input: Unit): BaseModel = Model
    }

  override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> {
    this.details = details
    return presenter
  }

  private data object Model : BaseModel
}
