package software.ralf.storymile.episodedetails

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents an episode in the caller's presenter backstack. */
interface EpisodeDetailsPresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT> {
  /** Creates a reusable episode destination. */
  interface Factory {
    /** Creates a destination for the selected episode snapshot. */
    fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*>
  }
}
