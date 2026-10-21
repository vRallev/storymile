package software.ralf.storymile.episodedetails.testing

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter

/** Records the last destination and renders [content] for its episode snapshot. */
class FakeEpisodeDetailsPresenterFactory(
  private val content: @Composable (EpisodeDetails) -> BaseModel = { Model(it) },
) : EpisodeDetailsPresenter.Factory {
  var details: EpisodeDetails? = null
    private set

  var presenter: EpisodeDetailsPresenter<*>? = null
    private set

  override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> {
    this.details = details
    return Presenter(details = details, content = content).also { presenter = it }
  }

  private class Presenter(
    private val details: EpisodeDetails,
    private val content: @Composable (EpisodeDetails) -> BaseModel,
  ) : EpisodeDetailsPresenter<BaseModel> {
    @Composable override fun present(input: Unit): BaseModel = content(details)
  }

  private data class Model(val details: EpisodeDetails) : BaseModel
}
