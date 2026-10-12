package software.ralf.storymile.playback

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the app's playback layer. */
interface PlaybackPresenter : ComposePresenter<Unit, PlaybackPresenter.Model> {
  /**
   * Playback content for the persistent bar and the full screen. Both are absent when the current
   * user's playlists contain no episodes.
   */
  interface Model : BaseModel {
    /** Playback bar whose measured height determines the sheet's collapsed height. */
    val collapsed: BaseModel?

    /** Content shown when playback expands to the full screen. */
    val expanded: BaseModel?
  }
}
